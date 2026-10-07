package com.aicampus.ai.service.core;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.knowledge.workspace.KnowledgeCatalogService;
import com.aicampus.common.dto.*;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionFeedback;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.aicampus.common.dto.InterviewSession;
import com.aicampus.common.dto.InterviewSessionAnswer;
import com.aicampus.common.dto.InterviewSessionAnswerRequest;
import com.aicampus.common.dto.InterviewSessionCreateRequest;
import com.aicampus.common.dto.InterviewSessionQuestion;
import com.aicampus.common.dto.InterviewSessionReport;
import com.aicampus.common.dto.LearningPlan;
import com.aicampus.common.dto.LearningPlanCreateRequest;
import com.aicampus.common.dto.LearningPlanReplanRequest;
import com.aicampus.common.dto.LearningTask;
import com.aicampus.common.dto.LearningTaskUpdateRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AiCareerCoreService {
    private static final int DEFAULT_WEEKLY_HOURS = 6;
    private static final int DEFAULT_DURATION_WEEKS = 8;
    private static final int DEFAULT_INTERVIEW_QUESTION_COUNT = 5;
    private static final int MAX_CONCURRENT_WRITE_ATTEMPTS = 3;
    private static final Set<String> TASK_STATUSES =
            Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "SKIPPED");

    private final AiCoachService aiCoachService;
    private final LearningPlanStore learningPlanStore;
    private final InterviewSessionStore interviewSessionStore;
    private final RecruitmentContextClient contextClient;
    private final InterviewPracticeService interviewPractice;
    private LearningEvidenceStore learningEvidenceStore = new InMemoryLearningEvidenceStore();
    private LearningWeeklyReviewStore learningWeeklyReviewStore = new InMemoryLearningWeeklyReviewStore();
    private KnowledgeCatalogService knowledgeCatalogService;
    private static final String ALGORITHM_VERSION = "career-evidence-v2";
    private static final String RUBRIC_VERSION = "interview-four-dimensions-v1";
    private final Object[] operationLocks =
            java.util.stream.IntStream.range(0, 128).mapToObj(i -> new Object()).toArray();

    @Autowired(required = false)
    public void setLearningEvidenceStore(LearningEvidenceStore store) {
        this.learningEvidenceStore = store;
    }

    @Autowired(required = false)
    public void setLearningWeeklyReviewStore(LearningWeeklyReviewStore store) {
        this.learningWeeklyReviewStore = store;
    }

    @Autowired(required = false)
    public void setKnowledgeCatalogService(KnowledgeCatalogService catalog) {
        this.knowledgeCatalogService = catalog;
        interviewPractice.setKnowledgeSourceValidator(id -> catalog.topic(id, "STUDENT"));
    }

    private Object operationLock(String key) {
        return operationLocks[Math.floorMod(key.hashCode(), operationLocks.length)];
    }

    public AiCareerCoreService(
            AiCoachService aiCoachService,
            LearningPlanStore learningPlanStore,
            InterviewSessionStore interviewSessionStore,
            RecruitmentContextClient contextClient) {
        this.aiCoachService = aiCoachService;
        this.learningPlanStore = learningPlanStore;
        this.interviewSessionStore = interviewSessionStore;
        this.contextClient = contextClient;
        this.interviewPractice = new InterviewPracticeService(aiCoachService, interviewSessionStore, contextClient, this);
    }

    public LearningPlan createLearningPlan(
            String studentId, String userRole, LearningPlanCreateRequest request) {
        requireStudentId(studentId);
        String resumeId = valueOr(request == null ? null : request.resumeId());
        String jobId = valueOr(request == null ? null : request.jobId());
        String matchId = valueOr(request == null ? null : request.matchId());
        RecruitmentContextClient.ValidatedContext context =
                contextClient.validate(studentId, resumeId, jobId, matchId, userRole);
        String targetRole =
                resolveTargetRole(request == null ? null : request.targetRole(), context);
        int weeklyHours = normalizeWeeklyHours(request == null ? null : request.weeklyHours());
        int durationWeeks =
                normalizeDurationWeeks(request == null ? null : request.durationWeeks());
        String input = fingerprint(
                planFingerprint(studentId, context, targetRole, weeklyHours, durationWeeks, null),
                valueOr(request == null ? null : request.startDate()),
                usefulStrings(request == null ? null : request.studyDays()).toString(),
                String.valueOf(request == null ? null : request.dailyMinutesCap()));
        synchronized (operationLock("create:" + studentId + input)) {
            LearningPlan cached =
                    learningPlanStore.listByStudent(studentId, 100).stream()
                            .filter(
                                    p ->
                                            p.analysisMetadata() != null
                                                    && input.equals(
                                                            p.analysisMetadata().inputFingerprint())
                                                    && "ACTIVE".equals(p.status())
                                                    && (!p.mocked()
                                                            || !aiCoachService.structuredAiEnabled()
                                                            || !aiCoachService.isModelConfigured()))
                            .findFirst()
                            .orElse(null);
            if (cached != null) return enrichEvidence(cached);
            LearningPlan plan =
                    generateLearningPlan(
                            studentId,
                            resumeId,
                            jobId,
                            matchId,
                            targetRole,
                            weeklyHours,
                            durationWeeks,
                            context,
                            null,
                            null);
            plan = applySchedule(plan,
                    request == null ? null : request.startDate(),
                    request == null ? null : request.studyDays(),
                    request == null ? null : request.dailyMinutesCap());
            plan = withAnalysisMetadata(plan, input, plan.mocked(), "learning-plan-v2");
            learningPlanStore.save(plan);
            return plan;
        }
    }

    public List<LearningPlan> listLearningPlans(String studentId, Integer limit) {
        requireStudentId(studentId);
        return learningPlanStore.listByStudent(studentId, normalizeListLimit(limit)).stream()
                .map(this::enrichEvidence)
                .toList();
    }

    public LearningPlan getLearningPlan(String planId, String studentId) {
        LearningPlan plan = requireLearningPlan(planId);
        requireOwner(plan.studentId(), studentId, "Learning plan");
        return enrichEvidence(plan);
    }

    public LearningTodayResponse learningToday(String planId, String studentId, String requestedDate) {
        LearningPlan plan = getLearningPlan(planId, studentId);
        String date = valueOr(requestedDate, LocalDate.now().toString());
        List<LearningTask> tasks = safeTasks(plan.tasks()).stream()
                .filter(t -> date.equals(t.taskDate()))
                .toList();
        int planned = tasks.stream().mapToInt(LearningTask::estimatedMinutes).sum();
        int actual = tasks.stream().mapToInt(t -> t.actualMinutes() == null ? 0 : t.actualMinutes()).sum();
        List<String> reminders = new ArrayList<>();
        tasks.stream().filter(t -> t.delayed() || t.deferredUntil() != null)
                .forEach(t -> reminders.add("已延期：" + t.title()));
        tasks.stream().filter(t -> "PENDING".equals(t.status()) || "IN_PROGRESS".equals(t.status()))
                .forEach(t -> reminders.add("待完成：" + t.title()));
        return new LearningTodayResponse(plan.planId(), date, tasks, List.copyOf(reminders), planned, actual);
    }

    public LearningWeeklyReview learningWeeklyReview(String planId, String studentId, Integer requestedWeek) {
        LearningPlan plan = getLearningPlan(planId, studentId);
        int week = requestedWeek == null ? 1 : requestedWeek;
        if (week < 1 || week > plan.durationWeeks()) throw new IllegalArgumentException("week is outside the plan");
        List<LearningTask> tasks = safeTasks(plan.tasks()).stream().filter(t -> t.week() == week).toList();
        int planned = tasks.stream().mapToInt(LearningTask::estimatedMinutes).sum();
        int actual = tasks.stream().mapToInt(t -> t.actualMinutes() == null ? 0 : t.actualMinutes()).sum();
        List<String> weak = tasks.stream().filter(t -> "SKIPPED".equals(t.status()) || t.delayed())
                .map(LearningTask::skillGap).filter(Objects::nonNull).distinct().toList();
        List<String> actions = tasks.stream().filter(t -> !"COMPLETED".equals(t.status()))
                .map(t -> "完成并提交：" + t.title()).toList();
        return new LearningWeeklyReview(plan.planId(), week, planned, actual,
                (int) tasks.stream().filter(t -> "COMPLETED".equals(t.status())).count(),
                (int) tasks.stream().filter(LearningTask::delayed).count(), weak, actions);
    }

    public List<LearningWeeklyReview> listLearningWeeklyReviews(String planId, String studentId) {
        LearningPlan plan = getLearningPlan(planId, studentId);
        return learningWeeklyReviewStore.list(plan.planId(), studentId);
    }

    public LearningWeeklyReview saveLearningWeeklyReview(
            String planId, String studentId, LearningWeeklyReviewRequest request) {
        LearningWeeklyReview calculated = learningWeeklyReview(
                planId, studentId, request == null ? null : request.week());
        int week = request != null && request.week() != null ? request.week() : calculated.week();
        LearningWeeklyReview review = new LearningWeeklyReview(
                calculated.planId(), week,
                request != null && request.plannedMinutes() != null
                        ? Math.max(0, request.plannedMinutes()) : calculated.plannedMinutes(),
                request != null && request.actualMinutes() != null
                        ? Math.max(0, request.actualMinutes()) : calculated.actualMinutes(),
                request != null && request.completedTasks() != null
                        ? Math.max(0, request.completedTasks()) : calculated.completedTasks(),
                calculated.delayedTasks(), calculated.weakSkills(), calculated.nextActions(),
                request == null ? null : valueOr(request.incompleteReason()),
                request == null ? null : valueOr(request.hardestTask()),
                request != null && Boolean.TRUE.equals(request.needsSplit()),
                request != null && request.mastery() != null
                        ? Math.max(0, Math.min(100, request.mastery())) : 0,
                request != null && request.nextWeekMinutes() != null
                        ? Math.max(0, request.nextWeekMinutes()) : 0,
                request == null ? List.of() : usefulStrings(request.newProblems()));
        learningWeeklyReviewStore.save(review, studentId);
        return review;
    }

    public List<String> learningReminders(String planId, String studentId, String requestedDate) {
        LearningTodayResponse today = learningToday(planId, studentId, requestedDate);
        LearningPlan plan = getLearningPlan(planId, studentId);
        List<String> reminders = new ArrayList<>(today.reminders());
        LocalDate parsedDate;
        try { parsedDate = LocalDate.parse(today.date()); } catch (DateTimeException ex) { parsedDate = LocalDate.now(); }
        final LocalDate reminderDate = parsedDate;
        safeTasks(plan.tasks()).stream()
                .filter(t -> t.deferredUntil() != null && t.deferredUntil().compareTo(reminderDate.toString()) < 0)
                .forEach(t -> reminders.add("已逾期：" + t.title()));
        safeTasks(plan.tasks()).stream()
                .filter(t -> safeList(t.evidence()).isEmpty()
                        && ("COMPLETED".equals(t.status()) || "IN_PROGRESS".equals(t.status())))
                .forEach(t -> reminders.add("成果待提交：" + t.title()));
        safeTasks(plan.tasks()).stream()
                .flatMap(t -> safeList(t.evidence()).stream())
                .filter(e -> "NEEDS_REVISION".equals(e.status()) || "FAILED".equals(e.status()))
                .forEach(e -> reminders.add("成果需要修改或重试：" + e.evidenceId()));
        return List.copyOf(new LinkedHashSet<>(reminders));
    }

    public LearningTask updateLearningTask(
            String planId, String taskId, String studentId, LearningTaskUpdateRequest request) {
        String status = normalizeTaskStatus(request == null ? null : request.status());
        String feedback = normalizeFeedback(request == null ? null : request.feedback());
        for (int attempt = 0; attempt < MAX_CONCURRENT_WRITE_ATTEMPTS; attempt++) {
            LearningPlan plan = requireLearningPlan(planId);
            requireOwner(plan.studentId(), studentId, "Learning plan");
            if (!"ACTIVE".equals(plan.status())) {
                throw new IllegalArgumentException("Only active learning plans can be updated");
            }
            validateKnowledgePlan(plan, false);
            Instant now = Instant.now();
            List<LearningTask> tasks = new ArrayList<>();
            LearningTask updatedTask = null;
            for (LearningTask task : safeTasks(plan.tasks())) {
                if (!task.taskId().equals(taskId)) {
                    tasks.add(task);
                    continue;
                }
                Instant completedAt =
                        "COMPLETED".equals(status)
                                ? (task.completedAt() == null ? now : task.completedAt())
                                : null;
                updatedTask =
                        new LearningTask(
                                task.taskId(),
                                task.week(),
                                task.title(),
                                task.description(),
                                task.skillGap(),
                                task.stage(),
                                task.acceptanceCriteria(),
                                task.practiceDeliverable(),
                                task.estimatedHours(),
                                status,
                                feedback,
                                completedAt,
                                now,
                                safeList(task.prerequisites()),
                                safeList(task.references()),
                                task.referenceStatus(),
                                safeList(task.evidence()),
                                task.taskDate(),
                                task.estimatedMinutes(),
                                safeList(task.dependencies()),
                                task.source(),
                                request == null || request.actualMinutes() == null
                                        ? task.actualMinutes() : request.actualMinutes(),
                                request != null && request.deferredUntil() != null,
                                request == null ? task.deferredUntil() : request.deferredUntil());
                tasks.add(updatedTask);
            }
            if (updatedTask == null) {
                throw new IllegalArgumentException("Learning task not found");
            }
            String planStatus =
                    tasks.stream().allMatch(task -> "COMPLETED".equals(task.status()))
                            ? "COMPLETED"
                            : "ACTIVE";
            if (learningPlanStore.updateActive(plan, copyPlan(plan, planStatus, tasks, now))) {
                return updatedTask;
            }
        }
        throw new IllegalStateException(
                "Learning plan was changed before the task could be updated");
    }

    public LearningPlan replan(
            String planId, String studentId, String userRole, LearningPlanReplanRequest request) {
        String reason = valueOr(request == null ? null : request.reason());
        if (reason == null) throw new IllegalArgumentException("replan reason is required");
        synchronized (operationLock("plan:" + planId)) {
            LearningPlan previous = requireLearningPlan(planId);
            requireOwner(previous.studentId(), studentId, "Learning plan");
            validateKnowledgePlan(previous, false);
            if (!"ACTIVE".equals(previous.status()))
                throw new IllegalArgumentException("Only active learning plans can be replanned");
            RecruitmentContextClient.ValidatedContext context =
                    contextClient.validate(
                            studentId,
                            previous.resumeId(),
                            previous.jobId(),
                            previous.matchId(),
                            userRole);
            int weeklyHours = normalizeWeeklyHours(request.weeklyHours(), previous.weeklyHours());
            int durationWeeks =
                    normalizeDurationWeeks(request.durationWeeks(), previous.durationWeeks());
            validateReplanBudget(previous.tasks(), weeklyHours, durationWeeks);
            String summary =
                    selectedInterviewSummary(request.interviewSessionId(), studentId, previous);
            String additionalContext =
                    reason
                            + (summary == null ? "" : "; 面试薄弱项：" + summary)
                            + learningEvidenceSummary(previous);
            if (Boolean.TRUE.equals(request.previewOnly())) {
                String draftInput =
                        planFingerprint(
                                studentId,
                                context,
                                previous.targetRole(),
                                weeklyHours,
                                durationWeeks,
                                additionalContext);
                LearningPlan cached =
                        learningPlanStore.listVersions(studentId, previous.rootPlanId()).stream()
                                .filter(
                                        p ->
                                                "DRAFT".equals(p.status())
                                                        && planId.equals(p.revisionOfPlanId())
                                                        && p.analysisMetadata() != null
                                                        && draftInput.equals(
                                                                p.analysisMetadata()
                                                                        .inputFingerprint()))
                                .findFirst()
                                .orElse(null);
                if (cached != null) return enrichEvidence(cached);
            }
            LearningPlan generated =
                    generateLearningPlan(
                            studentId,
                            previous.resumeId(),
                            previous.jobId(),
                            previous.matchId(),
                            previous.targetRole(),
                            weeklyHours,
                            durationWeeks,
                            context,
                            previous,
                            additionalContext);
            generated = attachKnowledgeInterviewSources(generated, request == null ? null : request.interviewSessionId(), studentId);
            if (hasScheduledSegments(previous.tasks()) || request != null && (valueOr(request.startDate()) != null
                    || request.studyDays() != null && !request.studyDays().isEmpty()
                    || request.dailyMinutesCap() != null)) {
                generated = applySchedule(generated,
                        valueOr(request.startDate()) == null ? previous.startDate() : request.startDate(),
                        safeList(request.studyDays()).isEmpty() ? previous.studyDays() : request.studyDays(),
                        request.dailyMinutesCap() == null ? previous.dailyMinutesCap() : request.dailyMinutesCap());
            }
            if (Boolean.TRUE.equals(request.previewOnly())) {
                LearningPlan draft =
                        copyPlan(generated, "DRAFT", generated.tasks(), generated.updatedAt());
                learningPlanStore.save(draft);
                return draft;
            }
            for (int attempt = 0; attempt < MAX_CONCURRENT_WRITE_ATTEMPTS; attempt++) {
                LearningPlan current = requireLearningPlan(planId);
                requireOwner(current.studentId(), studentId, "Learning plan");
                if (!"ACTIVE".equals(current.status()))
                    throw new IllegalStateException(
                            "Learning plan was changed before it could be replanned");
                validateReplanBudget(current.tasks(), weeklyHours, durationWeeks);
                LearningPlan revised =
                        copyPlan(
                                generated,
                                generated.status(),
                                preserveCompletedTasks(
                                        generated.tasks(),
                                        enrichEvidence(current).tasks(),
                                        weeklyHours,
                                        durationWeeks),
                                generated.updatedAt());
                if (learningPlanStore.replaceActiveWithRevision(
                        current,
                        copyPlan(current, "SUPERSEDED", current.tasks(), Instant.now()),
                        revised)) return revised;
            }
            throw new IllegalStateException(
                    "Learning plan was changed before it could be replanned");
        }
    }

    private LearningPlan attachKnowledgeInterviewSources(LearningPlan plan, String interviewSessionId, String studentId) {
        if (valueOr(interviewSessionId) == null) return plan;
        InterviewSession session = interviewSessionStore.findById(interviewSessionId).orElse(null);
        if (session == null || !studentId.equals(session.studentId())) return plan;
        List<InterviewSourceReference> refs = safeList(session.sourceReferences()).stream()
                .filter(ref -> "KNOWLEDGE".equals(ref.kind()) || "KNOWLEDGE_DOCUMENT".equals(ref.kind())).toList();
        if (refs.isEmpty()) return plan;
        String source = refs.stream().filter(ref -> "KNOWLEDGE".equals(ref.kind())).findFirst().map(ref -> {
            if (ref.sourceId() == null || ref.location() == null || !ref.location().contains("/v")) return null;
            return ref.sourceId() + ":" + ref.location().substring(ref.location().lastIndexOf("/v") + 2);
        }).orElse(null);
        if (source == null) {
            InterviewSourceReference ref = refs.get(0);
            if (ref.sourceId() != null && ref.sourceId().startsWith("KNOWLEDGE_DOCUMENT:") && ref.location() != null
                    && ref.location().startsWith("document/v"))
                source = ref.sourceId() + ":" + ref.location().substring("document/v".length());
        }
        if (source == null) return plan;
        final String inherited = source;
        List<LearningTask> tasks = safeTasks(plan.tasks()).stream().map(task -> new LearningTask(task.taskId(), task.week(),
                task.title(), task.description(), task.skillGap(), task.stage(), task.acceptanceCriteria(), task.practiceDeliverable(),
                task.estimatedHours(), task.status(), task.feedback(), task.completedAt(), task.updatedAt(), safeList(task.prerequisites()),
                safeList(task.references()), task.referenceStatus(), safeList(task.evidence()), task.taskDate(), task.estimatedMinutes(),
                safeList(task.dependencies()), inherited, task.actualMinutes(), task.delayed(), task.deferredUntil())).toList();
        return copyPlan(plan, plan.status(), tasks, plan.updatedAt());
    }

    public LearningPlan confirmLearningRevision(
            String planId, String studentId, String revisionId) {
        synchronized (operationLock("plan:" + planId)) {
            LearningPlan draft =
                    requireLearningPlan(requireText(revisionId, "revisionId is required"));
            requireOwner(draft.studentId(), studentId, "Learning plan");
            if (!planId.equals(draft.revisionOfPlanId()))
                throw new IllegalArgumentException("Draft does not revise the selected plan");
            validateKnowledgePlan(draft, true);
            if ("ACTIVE".equals(draft.status())) return enrichEvidence(draft);
            if (!"DRAFT".equals(draft.status()))
                throw new IllegalArgumentException("Selected learning revision is not a draft");
            for (int attempt = 0; attempt < MAX_CONCURRENT_WRITE_ATTEMPTS; attempt++) {
                LearningPlan current = requireLearningPlan(planId);
                requireOwner(current.studentId(), studentId, "Learning plan");
                if (!"ACTIVE".equals(current.status()) || draft.version() != current.version() + 1)
                    throw new IllegalArgumentException(
                            "The active plan has changed; generate a new preview");
                validateReplanBudget(current.tasks(), draft.weeklyHours(), draft.durationWeeks());
                LearningPlan active =
                        copyPlan(
                                draft,
                                "ACTIVE",
                                isKnowledgeActionDraft(draft)
                                        ? validatedKnowledgeRevisionTasks(draft, current)
                                        : preserveCompletedTasks(
                                                draft.tasks(),
                                                enrichEvidence(current).tasks(),
                                                draft.weeklyHours(),
                                                draft.durationWeeks()),
                                Instant.now());
                if (learningPlanStore.replaceActiveWithRevision(
                        current,
                        copyPlan(current, "SUPERSEDED", current.tasks(), Instant.now()),
                        active)) return active;
            }
            throw new IllegalStateException(
                    "Learning plan changed before confirmation could be saved");
        }
    }

    public List<LearningPlan> listLearningPlanVersions(String planId, String studentId) {
        LearningPlan plan = getLearningPlan(planId, studentId);
        return learningPlanStore.listVersions(studentId, plan.rootPlanId()).stream()
                .map(this::enrichEvidence)
                .toList();
    }

    public InterviewSession createInterviewSession(
            String studentId, String userRole, InterviewSessionCreateRequest request) {
        requireStudentId(studentId);
        return interviewPractice.create(studentId, userRole, request);
    }

    public InterviewSession createKnowledgeInterview(String studentId, String userRole, InterviewSessionCreateRequest request,
            String sourceId, String material, List<InterviewSourceReference> references, List<String> gaps, String stableSessionId) {
        requireStudentId(studentId);
        return interviewPractice.createKnowledgePractice(studentId, userRole, request, sourceId, material, references, gaps, stableSessionId);
    }


    public List<InterviewSession> listInterviewSessions(String studentId, Integer limit) {
        requireStudentId(studentId);
        return interviewPractice.listSessions(studentId, limit);
    }

    public InterviewSession getInterviewSession(String sessionId, String studentId) {
        return interviewPractice.get(sessionId, studentId);
    }

    public InterviewSession answerInterviewQuestion(
            String sessionId,
            String questionId,
            String studentId,
            InterviewSessionAnswerRequest request) {
        return interviewPractice.answer(sessionId, questionId, studentId, request, false);
    }


    public InterviewEvaluationResponse evaluateInterviewAnswer(
            String sessionId, String questionId, String studentId) {
        return interviewPractice.evaluate(sessionId, questionId, null, studentId, false);
    }


    public InterviewSessionReport finishInterviewSession(String sessionId, String studentId) {
        return interviewPractice.report(sessionId, studentId, false);
    }


    public InterviewPracticeService interviewPractice() { return interviewPractice; }


    private LearningPlan generateLearningPlan(
            String studentId,
            String resumeId,
            String jobId,
            String matchId,
            String targetRole,
            int weeklyHours,
            int durationWeeks,
            RecruitmentContextClient.ValidatedContext context,
            LearningPlan previous,
            String additionalContext) {
        String planningContext =
                buildPlanningContext(context, targetRole, weeklyHours, additionalContext);
        int firstWeeks = Math.min(12, durationWeeks);
        CareerPlanResponse generated =
                aiCoachService.careerPlan(
                        new CareerPlanRequest(
                                studentId,
                                targetRole,
                                context.resumeSkills(),
                                effectiveSkillGaps(context, targetRole),
                                planningContext,
                                firstWeeks,
                                weeklyHours,
                                context.requiredSkills()));
        if (durationWeeks > 12) {
            CareerPlanResponse second =
                    aiCoachService.careerPlan(
                            new CareerPlanRequest(
                                    studentId,
                                    targetRole,
                                    context.resumeSkills(),
                                    effectiveSkillGaps(context, targetRole),
                                    planningContext
                                            + "\n这是第13周后的续计划，返回week从1开始；此前任务技能："
                                            + safeList(generated.tasks()).stream()
                                                    .map(CareerLearningTask::targetSkill)
                                                    .toList(),
                                    durationWeeks - 12,
                                    weeklyHours,
                                    context.requiredSkills()));
            List<CareerLearningTask> combined = new ArrayList<>(safeList(generated.tasks()));
            for (CareerLearningTask task : safeList(second.tasks()))
                combined.add(
                        new CareerLearningTask(
                                task.week() + 12,
                                task.title(),
                                task.targetSkill(),
                                task.prerequisites(),
                                task.estimatedHours(),
                                task.exercise(),
                                task.acceptanceCriteria(),
                                task.deliverable(),
                                task.estimatedMinutes()));
            generated =
                    new CareerPlanResponse(
                            generated.studentId(),
                            generated.targetRole(),
                            generated.readinessScore(),
                            generated.summary(),
                            generated.milestones(),
                            generated.skillGaps(),
                            generated.weeklyActions(),
                            generated.portfolioTasks(),
                            generated.interviewFocus(),
                            generated.mocked() || second.mocked(),
                            combined);
        }
        if (previous != null && generated.mocked() && aiCoachService.structuredAiEnabled()) {
            throw new IllegalStateException("Unable to generate an AI learning plan revision");
        }
        String planId = "LP-" + UUID.randomUUID().toString().substring(0, 12);
        Instant now = Instant.now();
        List<String> effectiveGaps = effectiveSkillGaps(context, targetRole);
        List<String> gaps =
                effectiveGaps.isEmpty() ? usefulStrings(generated.skillGaps()) : effectiveGaps;
        List<LearningTask> tasks;
        if (!generated.mocked() && !safeList(generated.tasks()).isEmpty()) {
            tasks =
                    structuredTasks(
                            planId,
                            generated.tasks(),
                            durationWeeks,
                            weeklyHours,
                            context.resumeSkills(),
                            context.requiredSkills());
        } else {
            if (aiCoachService.isModelConfigured() && !generated.mocked())
                throw new IllegalStateException(
                        "Structured learning tasks are missing from the AI response");
            tasks =
                    createTasks(
                            planId,
                            targetRole,
                            durationWeeks,
                            weeklyHours,
                            gaps,
                            generated.weeklyActions());
        }
        if (previous != null) {
            tasks = preserveCompletedTasks(tasks, previous.tasks(), weeklyHours, durationWeeks);
        }
        LearningPlan plan =
                new LearningPlan(
                        planId,
                        previous == null ? planId : previous.rootPlanId(),
                        studentId,
                        resumeId,
                        jobId,
                        matchId,
                        targetRole,
                        context.snapshot(resumeId, jobId, matchId),
                        weeklyHours,
                        durationWeeks,
                        "ACTIVE",
                        previous == null ? 1 : previous.version() + 1,
                        previous == null ? null : previous.planId(),
                        tasks,
                        generated.mocked(),
                        now,
                        now,
                        additionalContext,
                        metadata(
                                planFingerprint(
                                        studentId,
                                        context,
                                        targetRole,
                                        weeklyHours,
                                        durationWeeks,
                                        additionalContext),
                                generated.mocked(),
                                "learning-plan-v2"));
        return plan;
    }

    private List<LearningTask> createTasks(
            String planId,
            String targetRole,
            int durationWeeks,
            int weeklyHours,
            List<String> skillGaps,
            List<String> actions) {
        List<String> gaps = usefulStrings(skillGaps);
        if (gaps.isEmpty()
                || (!targetRole.toLowerCase(Locale.ROOT).contains("java")
                        && gaps.stream()
                                .anyMatch(g -> g.toLowerCase(Locale.ROOT).contains("java")))) {
            String role = targetRole.toLowerCase(Locale.ROOT);
            gaps =
                    role.contains("运营") || role.contains("operation")
                            ? List.of("用户分析", "数据分析", "活动运营")
                            : role.contains("前端") || role.contains("frontend")
                                    ? List.of("JavaScript", "组件设计", "性能优化")
                                    : List.of("项目成果量化与岗位表达");
        }
        List<CareerLearningTask> tasks = new ArrayList<>();
        for (int week = 1; week <= durationWeeks; week++) {
            String skill = gaps.get((week - 1) % gaps.size());
            int first = weeklyHours / 2;
            tasks.add(
                    new CareerLearningTask(
                            week,
                            "第" + week + "周：" + skill + "实践",
                            skill,
                            List.of(),
                            first,
                            practiceExercise(skill, targetRole),
                            "说明场景、方案取舍和验证方法；提交实际练习记录。",
                            "练习说明与验证记录"));
            tasks.add(
                    new CareerLearningTask(
                            week,
                            "第" + week + "周：整理" + skill + "成果",
                            skill,
                            List.of(skill),
                            weeklyHours - first,
                            "基于实际练习整理本人负责内容、验证方法和结果。缺少数据时标为待补。",
                            "说明问题、个人行动和结果，至少提供一项测试记录、分析表或作品引用。",
                            "来自实际练习的成果描述和改进清单"));
        }
        return structuredTasks(planId, tasks, durationWeeks, weeklyHours, List.of(), gaps);
    }

    private static String practiceExercise(String skill, String role) {
        String text = skill.toLowerCase(Locale.ROOT);
        if (text.contains("redis") || text.contains("缓存"))
            return "实现查询缓存场景，说明TTL和失效策略，测试命中、未命中和过期，记录实际结果。";
        if (text.contains("mysql") || text.contains("索引") || text.equals("sql"))
            return "为练习数据表编写查询，用EXPLAIN比较索引前后的执行计划，记录数据量、查询条件和结果。";
        if (text.contains("java") && !text.contains("javascript"))
            return "实现集合或并发处理场景，解释边界条件、异常处理和方案取舍，提供可重复的测试和结果。";
        if (text.contains("spring")) return "实现含参数校验、业务逻辑和持久化的接口，验证正常请求、无效请求和异常路径并记录结果。";
        if (text.contains("react") || text.contains("vue") || text.contains("组件"))
            return "实现可复用表单组件，覆盖加载、空数据、校验和错误状态，测试键盘操作与关键交互。";
        if (text.contains("javascript")
                || text.contains("html")
                || text.contains("css")
                || role.contains("前端")) return "实现岗位相关交互页面，说明状态变化、异步错误处理和浏览器验证方法，记录实际测试结果。";
        if (text.contains("数据")
                || text.contains("分析")
                || text.contains("运营")
                || role.contains("运营"))
            return "选定校园活动或内容运营场景，定义目标用户与转化漏斗，使用明确标注的练习数据计算指标，提出假设、对照实验与复盘。";
        return "围绕" + role + "的" + skill + "要求完成具体案例，说明场景、行动、取舍和验证结果，缺少结果时标为待补。";
    }

    private List<LearningTask> preserveCompletedTasks(
            List<LearningTask> newTasks,
            List<LearningTask> previousTasks,
            int weeklyHours,
            int durationWeeks) {
        if (safeTasks(newTasks).stream().anyMatch(task -> valueOr(task.taskDate()) != null)) {
            Map<String, LearningTask> completed = new LinkedHashMap<>();
            for (LearningTask task : safeTasks(previousTasks))
                if ("COMPLETED".equals(task.status())) completed.putIfAbsent(task.taskId(), task);
            Set<String> ids = new HashSet<>();
            Map<Integer, Integer> weeklyMinutes = new HashMap<>();
            List<LearningTask> result = new ArrayList<>();
            for (LearningTask task : safeTasks(newTasks)) {
                if (!ids.add(task.taskId()))
                    throw new IllegalArgumentException("Scheduled revision contains duplicate task identifiers");
                LearningTask original = completed.get(task.taskId());
                if (original != null && (!Objects.equals(original.taskDate(), task.taskDate())
                        || taskMinutes(original) != taskMinutes(task)))
                    throw new IllegalArgumentException("Completed work has changed; generate a new preview");
                LearningTask preserved = original == null ? task : original;
                if (preserved.week() < 1 || preserved.week() > durationWeeks
                        || taskMinutes(preserved) <= 0
                        || weeklyMinutes.merge(preserved.week(), taskMinutes(preserved), Integer::sum) > weeklyHours * 60)
                    throw new IllegalArgumentException("Scheduled revision exceeds the weekly learning budget");
                result.add(preserved);
            }
            if (!ids.containsAll(completed.keySet()))
                throw new IllegalArgumentException("Completed work has changed; generate a new preview");
            // The preview already allocated every segment. Re-applying the legacy
            // two-task limit would delete segments and inflate the surviving ones.
            return List.copyOf(result);
        }
        if (hasScheduledSegments(previousTasks))
            return preserveSegmentedPlanWork(newTasks, previousTasks, weeklyHours, durationWeeks);
        Map<Integer, Map<String, LearningTask>> completedByWeek = new HashMap<>();
        for (LearningTask task : safeTasks(previousTasks)) {
            if ("COMPLETED".equals(task.status())) {
                completedByWeek
                        .computeIfAbsent(task.week(), ignored -> new HashMap<>())
                        .putIfAbsent(task.taskId(), task);
            }
        }
        List<LearningTask> result = new ArrayList<>();
        Set<String> completedTaskIds = new HashSet<>();
        for (int week = 1; week <= durationWeeks; week++) {
            int currentWeek = week;
            List<LearningTask> completed = completedByWeek
                    .getOrDefault(week, Map.of())
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(LearningTask::taskId))
                    .toList();
            for (LearningTask task : completed) {
                if (completedTaskIds.add(task.taskId())) {
                    result.add(task);
                }
            }
            int completedMinutes = completed.stream().mapToInt(AiCareerCoreService::taskMinutes).sum();
            int remainingMinutes = weeklyHours * 60 - completedMinutes;
            if (remainingMinutes <= 0) {
                continue;
            }
            Set<String> selectedTaskIds = new HashSet<>(completedTaskIds);
            List<LearningTask> candidates = newTasks.stream()
                    .filter(task -> task.week() == currentWeek)
                    .filter(task -> selectedTaskIds.add(task.taskId()))
                    .limit(Math.max(0, 2 - completed.size()))
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "Completed tasks leave no remaining task slot for week " + week);
            }
            for (int index = 0; index < candidates.size(); index++) {
                LearningTask task = candidates.get(index);
                int allocatedMinutes =
                        remainingMinutes / candidates.size()
                                + (index < remainingMinutes % candidates.size() ? 1 : 0);
                result.add(copyTaskWithMinutes(task, allocatedMinutes));
            }
        }
        // A revision can pass through the preservation step more than once
        // (generation and confirmation both protect completed work).  Older
        // persisted plans may also already contain duplicate task IDs.  Keep
        // one canonical task per ID, preferring the completed copy so the
        // student's history and evidence are never lost.
        Map<String, LearningTask> uniqueTasks = new LinkedHashMap<>();
        for (LearningTask task : result) {
            LearningTask existing = uniqueTasks.get(task.taskId());
            if (existing == null
                    || (!"COMPLETED".equals(existing.status())
                            && "COMPLETED".equals(task.status()))) {
                uniqueTasks.put(task.taskId(), task);
            }
        }
        return uniqueTasks.values().stream()
                .sorted(
                        Comparator.comparingInt(LearningTask::week)
                                .thenComparing(LearningTask::taskId))
                .toList();
    }

    private InterviewSessionQuestion nextUnansweredQuestion(InterviewSession session) {
        Set<String> answered = new HashSet<>();
        for (InterviewSessionAnswer answer : safeAnswers(session.answers())) {
            answered.add(answer.questionId());
        }
        return session.questions().stream()
                .filter(question -> !answered.contains(question.questionId()))
                .min(Comparator.comparingInt(InterviewSessionQuestion::order))
                .orElse(null);
    }

    private InterviewSessionQuestion followUpQuestion(
            InterviewSession session,
            InterviewSessionQuestion mainQuestion,
            String prompt,
            boolean mocked) {
        String questionId = mainQuestion.questionId() + "-F1";
        return new InterviewSessionQuestion(
                questionId,
                mainQuestion.order() + 5,
                mainQuestion.mainQuestionId(),
                mainQuestion.category(),
                mainQuestion.difficulty(),
                "请补充一个可量化的结果、你做过的取舍，以及验证结果的方式：" + mainQuestion.question(),
                List.of("给出可量化结果", "说明关键取舍", "描述验证证据"),
                true,
                mocked ? "RULE_EVALUATION" : "DASHSCOPE_EVALUATION");
    }

    private InterviewSessionReport toReport(
            String sessionId, List<InterviewQuestionFeedback> feedback) {
        int overallScore =
                (int)
                        Math.round(
                                feedback.stream()
                                        .mapToInt(InterviewQuestionFeedback::score)
                                        .average()
                                        .orElse(0));
        List<String> strengths =
                mergeDistinct(
                        feedback.stream()
                                .flatMap(item -> safeList(item.strengths()).stream())
                                .toList());
        List<String> gaps =
                mergeDistinct(
                        feedback.stream().flatMap(item -> safeList(item.gaps()).stream()).toList());
        List<String> recommendations =
                mergeDistinct(
                        feedback.stream()
                                .flatMap(item -> safeList(item.suggestions()).stream())
                                .toList());
        boolean mocked = feedback.stream().anyMatch(InterviewQuestionFeedback::mocked);
        return new InterviewSessionReport(
                sessionId,
                overallScore,
                strengths,
                gaps,
                recommendations,
                List.copyOf(feedback),
                Instant.now(),
                mocked);
    }

    private static String buildPlanningContext(
            RecruitmentContextClient.ValidatedContext context,
            String targetRole,
            int weeklyHours,
            String additionalContext) {
        List<String> sections = new ArrayList<>();
        if (context.resume() != null && valueOr(context.resume().diagnosis()) != null) {
            sections.add("简历诊断：" + context.resume().diagnosis().trim());
        }
        sections.add("目标岗位：" + targetRole);
        sections.add("当前声明技能（尚需材料核对）：" + displayList(context.resumeSkills(), "暂未提供"));
        sections.add("岗位要求技能：" + displayList(context.requiredSkills(), "暂未提供"));
        sections.add(
                "待补能力或材料：" + displayList(effectiveSkillGaps(context, targetRole), "请优先补充可验证成果"));
        sections.add("材料证据与来源：" + contextMaterial(context));
        sections.add("每周可投入时间：" + weeklyHours + "小时");
        if (valueOr(additionalContext) != null) {
            sections.add("本次调整依据：" + additionalContext.trim());
        }
        return String.join("\n", sections);
    }

    private static void validateReplanBudget(
            List<LearningTask> previousTasks, int weeklyHours, int durationWeeks) {
        Map<Integer, Integer> completedMinutesByWeek = new HashMap<>();
        for (LearningTask task : safeTasks(previousTasks)) {
            if (!"COMPLETED".equals(task.status())) {
                continue;
            }
            if (task.week() > durationWeeks) {
                throw new IllegalArgumentException(
                        "Cannot shorten the plan past a completed task in week " + task.week());
            }
            completedMinutesByWeek.merge(task.week(), taskMinutes(task), Integer::sum);
        }
        for (Map.Entry<Integer, Integer> entry : completedMinutesByWeek.entrySet()) {
            if (entry.getValue() > weeklyHours * 60) {
                throw new IllegalArgumentException(
                        "weeklyHours is lower than completed work in week " + entry.getKey());
            }
        }
    }

    private static int taskMinutes(LearningTask task) {
        return task.estimatedMinutes() > 0 ? task.estimatedMinutes() : task.estimatedHours() * 60;
    }

    private static LearningTask copyTaskWithMinutes(LearningTask task, int estimatedMinutes) {
        return new LearningTask(
                task.taskId(),
                task.week(),
                task.title(),
                task.description(),
                task.skillGap(),
                task.stage(),
                task.acceptanceCriteria(),
                task.practiceDeliverable(),
                (estimatedMinutes + 59) / 60,
                task.status(),
                task.feedback(),
                task.completedAt(),
                task.updatedAt(),
                safeList(task.prerequisites()),
                safeList(task.references()),
                task.referenceStatus(),
                safeList(task.evidence()),
                task.taskDate(),
                Math.max(0, estimatedMinutes),
                safeList(task.dependencies()),
                task.source(),
                task.actualMinutes(),
                task.delayed(),
                task.deferredUntil());
    }

    private String selectedInterviewSummary(String sessionId, String studentId, LearningPlan plan) {
        if (valueOr(sessionId) == null) {
            return null;
        }
        InterviewSession session = getInterviewSession(sessionId, studentId);
        if (session.report() == null || !"COMPLETED".equals(session.status())) {
            throw new IllegalArgumentException("Selected interview session is not completed");
        }
        if (!sameTargetRole(plan.targetRole(), session.targetRole())) {
            throw new IllegalArgumentException(
                    "Selected interview session has a different target role");
        }
        if (!sameReference(plan.resumeId(), session.resumeId())
                || !sameReference(plan.jobId(), session.jobId())
                || !sameReference(plan.matchId(), session.matchId())) {
            throw new IllegalArgumentException(
                    "Selected interview session does not match the learning plan context");
        }
        return "薄弱项："
                + String.join("；", safeList(session.report().gaps()))
                + "；对应练习："
                + String.join("；", safeList(session.report().recommendations()));
    }

    public LearningEvidence submitLearningEvidence(
            String planId, String taskId, String studentId, LearningEvidenceRequest request) {
        String description =
                requireText(request == null ? null : request.description(), "成果说明不能为空");
        if (description.length() > 8000) throw new IllegalArgumentException("成果说明不能超过8000字");
        List<String> links = usefulStrings(request == null ? null : request.links());
        if (links.size() > 10) throw new IllegalArgumentException("最多提交10个引用链接");
        for (String link : links) {
            try {
                java.net.URI uri = java.net.URI.create(link);
                if (link.length() > 2048
                        || uri.getHost() == null
                        || !List.of("http", "https").contains(uri.getScheme())
                        || uri.getUserInfo() != null) throw new IllegalArgumentException();
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("作品链接须为有效的http或https地址");
            }
        }
        synchronized (operationLock("evidence:" + taskId)) {
            LearningPlan plan = requireLearningPlan(planId);
            requireOwner(plan.studentId(), studentId, "Learning plan");
            LearningTask task =
                    plan.tasks().stream()
                            .filter(t -> taskId.equals(t.taskId()))
                            .findFirst()
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Learning task not found"));
            requireKnowledgeTaskSource(task);
            if (!"ACTIVE".equals(plan.status()) && !"COMPLETED".equals(plan.status()))
                throw new IllegalArgumentException(
                        "Only the selected active or completed learning plan can receive evidence");
            String input =
                    fingerprint(
                            studentId,
                            taskId,
                            task.description(),
                            task.acceptanceCriteria(),
                            description,
                            links.toString(),
                            ALGORITHM_VERSION,
                            modelKey("learning-evaluation-v1"));
            LearningEvidence existing =
                    learningEvidenceStore.findByFingerprint(studentId, taskId, input).orElse(null);
            if (existing != null
                    && ("SUCCEEDED".equals(existing.status())
                            || "RECORDED".equals(existing.status()))) return existing;
            Instant now = Instant.now();
            String id =
                    existing == null
                            ? "LE-" + java.util.UUID.randomUUID().toString()
                            : existing.evidenceId();
            Instant submitted = existing == null ? now : existing.submittedAt();
            LearningEvidence pending =
                    new LearningEvidence(
                            id,
                            planId,
                            taskId,
                            studentId,
                            description,
                            links,
                            "EVALUATING",
                            null,
                            null,
                            metadata(
                                    input,
                                    !aiCoachService.isModelConfigured(),
                                    "learning-evaluation-v1"),
                            submitted,
                            null);
            learningEvidenceStore.save(pending);
            LearningEvidence result;
            try {
                requireKnowledgeTaskSource(task);
                LearningEvidenceEvaluation evaluation =
                        aiCoachService.evaluateLearningEvidence(task, description, links);
                requireKnowledgeTaskSource(task);
                result =
                        new LearningEvidence(
                                id,
                                planId,
                                taskId,
                                studentId,
                                description,
                                links,
                                evaluation.mocked()
                                        ? "RECORDED"
                                        : (evaluation.score() < 70 ? "NEEDS_REVISION" : "SUCCEEDED"),
                                evaluation,
                                null,
                                metadata(input, evaluation.mocked(), "learning-evaluation-v1"),
                                submitted,
                                evaluation.mocked() ? null : Instant.now());
            } catch (RuntimeException ex) {
                result =
                        new LearningEvidence(
                                id,
                                planId,
                                taskId,
                                studentId,
                                description,
                                links,
                                "FAILED",
                                null,
                                "评价暂时不可用，成果已保存。再次提交相同成果可重试评价。",
                                pending.analysisMetadata(),
                                submitted,
                                null);
            }
            learningEvidenceStore.save(result);
            return result;
        }
    }

    public List<LearningEvidence> listLearningEvidence(String planId, String taskId, String studentId) {
        LearningPlan plan = requireLearningPlan(planId);
        requireOwner(plan.studentId(), studentId, "Learning plan");
        LearningTask task = safeTasks(plan.tasks()).stream().filter(t -> taskId.equals(t.taskId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Learning task not found"));
        List<LearningEvidence> evidence = learningEvidenceStore.listByTask(studentId, taskId);
        try { requireKnowledgeTaskSource(task); return evidence; }
        catch (IllegalArgumentException ex) { return evidence.stream().map(AiCareerCoreService::maskedKnowledgeEvidence).toList(); }
    }

    public LearningEvidence retryLearningEvidence(String planId, String taskId, String evidenceId, String studentId) {
        LearningEvidence evidence = findEvidence(planId, taskId, evidenceId, studentId);
        if (!List.of("FAILED", "NEEDS_REVISION").contains(evidence.status())) return evidence;
        return submitLearningEvidence(planId, taskId, studentId,
                new LearningEvidenceRequest(evidence.description(), evidence.links()));
    }

    public LearningEvidence confirmLearningEvidence(String planId, String taskId, String evidenceId, String studentId) {
        LearningEvidence evidence = findEvidence(planId, taskId, evidenceId, studentId);
        if (!List.of("SUCCEEDED", "RECORDED", "RESUME_CANDIDATE", "CONFIRMED").contains(evidence.status()))
            throw new IllegalArgumentException("Only evaluated evidence can be confirmed");
        LearningEvidence confirmed = withEvidenceFlags(evidence, "CONFIRMED", true, evidence.resumeCandidate());
        learningEvidenceStore.save(confirmed);
        return confirmed;
    }

    public LearningEvidence addEvidenceToResumeCandidate(String planId, String taskId, String evidenceId, String studentId) {
        LearningEvidence evidence = findEvidence(planId, taskId, evidenceId, studentId);
        if (!evidence.confirmed() || !List.of("CONFIRMED", "RESUME_CANDIDATE").contains(evidence.status()))
            throw new IllegalArgumentException("Student confirmation is required before a resume candidate can be created");
        LearningEvidence candidate = withEvidenceFlags(evidence, "RESUME_CANDIDATE", evidence.confirmed(), true);
        learningEvidenceStore.save(candidate);
        return candidate;
    }

    private LearningEvidence findEvidence(String planId, String taskId, String evidenceId, String studentId) {
        LearningPlan raw = requireLearningPlan(planId);
        requireOwner(raw.studentId(), studentId, "Learning plan");
        raw.tasks().stream().filter(task -> taskId.equals(task.taskId())).findFirst().ifPresent(this::requireKnowledgeTaskSource);
        return listLearningEvidence(planId, taskId, studentId).stream()
                .filter(e -> evidenceId.equals(e.evidenceId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Learning evidence not found"));
    }

    /** Shared guard for the knowledge action bridge and the legacy confirmation endpoint. */
    public void validateKnowledgeDraft(String planId, String studentId) {
        LearningPlan plan = requireLearningPlan(planId);
        requireOwner(plan.studentId(), studentId, "Learning plan");
        validateKnowledgePlan(plan, true);
    }

    private void validateKnowledgePlan(LearningPlan plan, boolean verifyBudget) {
        safeTasks(plan.tasks()).forEach(this::requireKnowledgeTaskSource);
        if (verifyBudget && isKnowledgeActionDraft(plan)) validateKnowledgeBudget(plan);
    }

    private void requireKnowledgeTaskSource(LearningTask task) {
        if ("SOURCE_UNAVAILABLE".equals(task.referenceStatus()))
            throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览");
        for (LearningReference reference : safeList(task.references())) validateKnowledgeReference(reference);
        String source = task.source();
        if (source == null) return;
        if (source.startsWith("KNOWLEDGE_DOCUMENT:")) {
            String[] parts = source.split(":", 3);
            if (parts.length != 3) throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览");
            try { validateKnowledgeReference(new LearningReference(parts[1], "", "", task.description(), Integer.valueOf(parts[2]))); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览"); }
            return;
        }
        if (!source.startsWith("KNOWLEDGE:")) return;
        String[] parts = source.split(":", 3);
        if (knowledgeCatalogService == null || parts.length != 3)
            throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览");
        com.aicampus.common.dto.KnowledgeWorkspaceModels.KnowledgeTopic topic;
        try { topic = knowledgeCatalogService.topic(parts[1], "STUDENT"); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览"); }
        if (topic == null || !Integer.toString(topic.version()).equals(parts[2]))
            throw new IllegalArgumentException("知识资料已更新，请重新选择资料并预览");
    }

    private com.aicampus.common.dto.KnowledgeWorkspaceModels.KnowledgeLibraryDocument validateKnowledgeReference(LearningReference reference) {
        if (knowledgeCatalogService == null || reference == null || valueOr(reference.documentId()) == null)
            throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览");
        com.aicampus.common.dto.KnowledgeWorkspaceModels.KnowledgeLibraryDocument document;
        try { document = knowledgeCatalogService.library(reference.documentId(), "STUDENT"); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览"); }
        if (document == null) throw new IllegalArgumentException("知识来源已不可用，请重新选择资料并预览");
        if (reference.documentVersion() != null && reference.documentVersion() != document.version()
                || valueOr(reference.snippet()) == null || !document.content().contains(reference.snippet()))
            throw new IllegalArgumentException("知识资料已更新，请重新选择资料并预览");
        return document;
    }

    public void validateKnowledgeDocumentReference(InterviewSourceReference reference) {
        if (reference == null || reference.sourceId() == null || !reference.sourceId().startsWith("KNOWLEDGE_DOCUMENT:"))
            throw new IllegalArgumentException("Knowledge source is unavailable or updated");
        Integer version;
        try {
            if (reference.location() == null || !reference.location().startsWith("document/v"))
                throw new IllegalArgumentException();
            version = Integer.valueOf(reference.location().substring("document/v".length()));
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Knowledge source is unavailable or updated"); }
        validateKnowledgeReference(new LearningReference(reference.sourceId().substring("KNOWLEDGE_DOCUMENT:".length()),
                "", "", reference.quote(), version));
    }

    public List<InterviewSourceReference> learningTaskSourceReferences(String planId, String taskId, String studentId) {
        LearningPlan plan = requireLearningPlan(planId);
        requireOwner(plan.studentId(), studentId, "Learning plan");
        LearningTask task = safeTasks(plan.tasks()).stream().filter(t -> taskId.equals(t.taskId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Learning task source was not found"));
        requireKnowledgeTaskSource(task);
        List<InterviewSourceReference> references = new ArrayList<>();
        for (LearningReference reference : safeList(task.references())) {
            var document = validateKnowledgeReference(reference);
            references.add(new InterviewSourceReference("KNOWLEDGE_DOCUMENT:" + document.documentId(), "KNOWLEDGE_DOCUMENT",
                    reference.snippet(), "document/v" + document.version()));
        }
        if (task.source() != null && task.source().startsWith("KNOWLEDGE:")) {
            String[] parts = task.source().split(":", 3);
            var topic = knowledgeCatalogService.topic(parts[1], "STUDENT");
            String material = Objects.toString(topic.content(), "") + "\n" + Objects.toString(topic.example(), "")
                    + "\n" + Objects.toString(topic.practicePrompt(), "");
            references.add(new InterviewSourceReference("KNOWLEDGE:" + topic.id(), "KNOWLEDGE", material,
                    "topic/content/v" + topic.version()));
        }
        return List.copyOf(references);
    }

    private LearningTask knowledgePublicTask(LearningTask task) {
        try { requireKnowledgeTaskSource(task); return task; }
        catch (IllegalArgumentException ex) {
            return new LearningTask(task.taskId(), task.week(), "知识来源已更新或不可用", "请重新选择有权阅读的知识资料，原任务进度与学生成果已保留。",
                    task.skillGap(), task.stage(), "请重新预览当前资料的验收标准", "学生实践成果", task.estimatedHours(),
                    task.status(), null, task.completedAt(), task.updatedAt(), safeList(task.prerequisites()),
                    List.of(), "SOURCE_UNAVAILABLE", safeList(task.evidence()).stream().map(AiCareerCoreService::maskedKnowledgeEvidence).toList(), task.taskDate(), task.estimatedMinutes(),
                    safeList(task.dependencies()), task.source(), task.actualMinutes(), task.delayed(), task.deferredUntil());
        }
    }

    private static LearningEvidence maskedKnowledgeEvidence(LearningEvidence evidence) {
        return new LearningEvidence(evidence.evidenceId(), evidence.planId(), evidence.taskId(), evidence.studentId(),
                evidence.description(), safeList(evidence.links()), evidence.status(), null,
                "知识来源已更新或不可用，学生提交内容已保留，评价依据暂不可查看。", evidence.analysisMetadata(),
                evidence.submittedAt(), evidence.evaluatedAt(), evidence.confirmed(), evidence.resumeCandidate());
    }

    private static boolean isKnowledgeActionDraft(LearningPlan plan) {
        return plan.analysisMetadata() != null && "knowledge-actions-v1".equals(plan.analysisMetadata().algorithmVersion());
    }

    private List<LearningTask> validatedKnowledgeRevisionTasks(LearningPlan draft, LearningPlan current) {
        validateKnowledgePlan(current, false);
        List<LearningTask> currentTasks = enrichEvidence(current).tasks();
        for (LearningTask task : currentTasks) {
            LearningTask copied = draft.tasks().stream().filter(candidate -> task.taskId().equals(candidate.taskId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("知识调整草稿不能删除已有任务或成果，请重新预览"));
            if (!task.equals(copied)) throw new IllegalArgumentException("原计划任务或成果已变化，请重新预览");
        }
        validateKnowledgeBudget(draft);
        return draft.tasks();
    }

    private static void validateKnowledgeBudget(LearningPlan plan) {
        LocalDate start;
        try { start = LocalDate.parse(plan.startDate()); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("知识调整计划缺少有效开始日期"); }
        if (plan.dailyMinutesCap() <= 0 || plan.weeklyHours() <= 0 || plan.durationWeeks() <= 0)
            throw new IllegalArgumentException("知识调整计划缺少有效时间预算");
        Set<DayOfWeek> days = new HashSet<>();
        try { for (String day : safeList(plan.studyDays())) days.add(DayOfWeek.valueOf(day)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("知识调整计划的学习日无效"); }
        if (days.isEmpty()) throw new IllegalArgumentException("请选择学习日");
        Map<String, Integer> daily = new HashMap<>();
        Map<Integer, Integer> weekly = new HashMap<>();
        Map<String, LocalDate> dates = new HashMap<>();
        Map<String, Integer> order = new HashMap<>();
        for (LearningTask task : safeTasks(plan.tasks())) {
            LocalDate date;
            try { date = task.taskDate() == null || task.taskDate().isBlank()
                    ? start.plusWeeks(Math.max(0, task.week() - 1)) : LocalDate.parse(task.taskDate()); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("任务缺少计划日期，请先在学习路径安排日程"); }
            long offset = java.time.temporal.ChronoUnit.DAYS.between(start, date);
            if (offset < 0 || offset >= plan.durationWeeks() * 7L || task.week() != offset / 7 + 1 || !days.contains(date.getDayOfWeek()))
                throw new IllegalArgumentException("任务日期与计划周或学习日不一致，请先调整原计划日程");
            int minutes = taskMinutes(task);
            if (minutes <= 0 || dates.putIfAbsent(task.taskId(), date) != null)
                throw new IllegalArgumentException("任务时间或标识无效");
            order.put(task.taskId(), order.size());
            if (daily.merge(date.toString(), minutes, Integer::sum) > plan.dailyMinutesCap())
                throw new IllegalArgumentException("知识调整超过每日时间上限：" + date);
            if (weekly.merge(task.week(), minutes, Integer::sum) > plan.weeklyHours() * 60)
                throw new IllegalArgumentException("知识调整超过每周时间预算：" + task.week());
        }
        for (LearningTask task : safeTasks(plan.tasks())) for (String prerequisite : safeList(task.dependencies()).isEmpty()
                ? safeList(task.prerequisites()) : safeList(task.dependencies())) {
            LocalDate prior = dates.get(prerequisite);
            if (!safeList(task.dependencies()).isEmpty() && prior == null)
                throw new IllegalArgumentException("任务前置依赖不存在");
            if (task.taskId().equals(prerequisite)) throw new IllegalArgumentException("任务不能依赖自身");
            if (prior != null && (prior.isAfter(dates.get(task.taskId()))
                    || prior.equals(dates.get(task.taskId())) && order.get(prerequisite) >= order.get(task.taskId())))
                throw new IllegalArgumentException("任务前置依赖顺序无效");
        }
    }

    private static LearningEvidence withEvidenceFlags(LearningEvidence evidence, String status,
            boolean confirmed, boolean resumeCandidate) {
        return new LearningEvidence(evidence.evidenceId(), evidence.planId(), evidence.taskId(), evidence.studentId(),
                evidence.description(), safeList(evidence.links()), status, evidence.evaluation(), evidence.error(),
                evidence.analysisMetadata(), evidence.submittedAt(), evidence.evaluatedAt(), confirmed, resumeCandidate);
    }

    private LearningPlan enrichEvidence(LearningPlan plan) {
        List<LearningTask> tasks =
                plan.tasks().stream()
                        .map(
                                t ->
                                        new LearningTask(
                                                t.taskId(),
                                                t.week(),
                                                t.title(),
                                                t.description(),
                                                t.skillGap(),
                                                t.stage(),
                                                t.acceptanceCriteria(),
                                                t.practiceDeliverable(),
                                                t.estimatedHours(),
                                                t.status(),
                                                t.feedback(),
                                                t.completedAt(),
                                                t.updatedAt(),
                                                safeList(t.prerequisites()),
                                                safeList(t.references()),
                                                t.referenceStatus(),
                                                learningEvidenceStore.listByTask(
                                                        plan.studentId(), t.taskId()),
                                                t.taskDate(),
                                                t.estimatedMinutes(),
                                                safeList(t.dependencies()),
                                                t.source(),
                                                t.actualMinutes(),
                                                t.delayed(),
                                                t.deferredUntil()))
                        .map(this::knowledgePublicTask)
                        .toList();
        LearningPlan result = copyPlan(plan, plan.status(), tasks, plan.updatedAt());
        if (tasks.stream().anyMatch(task -> "SOURCE_UNAVAILABLE".equals(task.referenceStatus())))
            return new LearningPlan(result.planId(), result.rootPlanId(), result.studentId(), result.resumeId(), result.jobId(),
                    result.matchId(), result.targetRole(), result.contextSnapshot(), result.weeklyHours(), result.durationWeeks(),
                    result.startDate(), result.studyDays(), result.dailyMinutesCap(), result.status(), result.version(),
                    result.revisionOfPlanId(), result.tasks(), result.mocked(), result.createdAt(), result.updatedAt(),
                    "部分知识来源已更新或不可用，保留任务进度与学生成果", result.analysisMetadata());
        return result;
    }

    private String learningEvidenceSummary(LearningPlan plan) {
        List<String> lines = new ArrayList<>();
        for (LearningTask task : safeTasks(plan.tasks()))
            for (LearningEvidence evidence :
                    learningEvidenceStore.listByTask(plan.studentId(), task.taskId())) {
                lines.add(
                        task.skillGap()
                                + "：学生成果说明="
                                + evidence.description()
                                + "；链接仅作引用="
                                + evidence.links()
                                + "；评价状态="
                                + evidence.status()
                                + (evidence.evaluation() == null
                                        ? ""
                                        : "；待改善="
                                                + String.join(
                                                        "、",
                                                        safeList(evidence.evaluation().gaps()))));
            }
        return lines.isEmpty() ? "" : "; 已提交学习成果（完成状态不代表掌握）：" + String.join("；", lines);
    }

    private List<LearningTask> structuredTasks(
            String planId,
            List<CareerLearningTask> generated,
            int weeks,
            int weeklyHours,
            List<String> existingSkills,
            List<String> prioritySkills) {
        Map<Integer, Integer> minutesByWeek = new HashMap<>();
        List<LearningTask> tasks = new ArrayList<>();
        Set<String> previousSkills =
                safeList(existingSkills).stream()
                        .map(com.aicampus.common.evidence.SkillOntology::normalize)
                        .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Map<String, List<LearningReference>> referenceCache = new HashMap<>();
        java.util.LinkedHashMap<String, Integer> priorities = new java.util.LinkedHashMap<>();
        for (String skill : safeList(prioritySkills))
            priorities.putIfAbsent(
                    com.aicampus.common.evidence.SkillOntology.normalize(skill), priorities.size());
        List<CareerLearningTask> pending = new ArrayList<>(generated);
        while (!pending.isEmpty()) {
            int currentWeek =
                    pending.stream().mapToInt(CareerLearningTask::week).min().orElseThrow();
            CareerLearningTask task =
                    pending.stream()
                            .filter(t -> t.week() == currentWeek)
                            .filter(
                                    t ->
                                            safeList(t.prerequisites()).stream()
                                                    .allMatch(
                                                            prerequisite ->
                                                                    previousSkills.contains(
                                                                            com.aicampus.common
                                                                                    .evidence
                                                                                    .SkillOntology
                                                                                    .normalize(
                                                                                            prerequisite))))
                            .min(
                                    Comparator.comparingInt(
                                            t ->
                                                    priorities.getOrDefault(
                                                            com.aicampus.common.evidence
                                                                    .SkillOntology.normalize(
                                                                    t.targetSkill()),
                                                            Integer.MAX_VALUE)))
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "AI learning prerequisite must be scheduled"
                                                        + " first"));
            pending.remove(task);

            int minutes = task.durationMinutes();
            if (task.week() < 1
                    || task.week() > weeks
                    || minutes < 1
                    || minutes > weeklyHours * 60)
                throw new IllegalStateException(
                        "AI learning task exceeds the requested weekly budget");
            String skill =
                    requireText(task.targetSkill(), "AI learning task is missing its target skill");
            for (String prerequisite : safeList(task.prerequisites()))
                if (!previousSkills.contains(
                        com.aicampus.common.evidence.SkillOntology.normalize(prerequisite)))
                    throw new IllegalStateException(
                            "AI learning prerequisite must be scheduled first");
            minutesByWeek.merge(task.week(), minutes, Integer::sum);
            if (minutesByWeek.get(task.week()) > weeklyHours * 60)
                throw new IllegalStateException(
                        "AI learning tasks exceed the requested weekly budget");
            List<LearningReference> references =
                    referenceCache.computeIfAbsent(skill, value -> currentLearningReferences(value));
            tasks.add(
                    new LearningTask(
                            planId + "-W" + task.week() + "-T" + (tasks.size() + 1),
                            task.week(),
                            requireText(task.title(), "AI task title is required"),
                            requireText(task.exercise(), "AI task exercise is required"),
                            skill,
                            stageForWeek(task.week(), weeks),
                            requireText(
                                    task.acceptanceCriteria(),
                                    "AI task acceptance criteria is required"),
                            requireText(task.deliverable(), "AI task deliverable is required"),
                            task.estimatedHours(),
                            "PENDING",
                            null,
                            null,
                            Instant.now(),
                            safeList(task.prerequisites()),
                            references,
                            references.isEmpty() ? "NO_MATCHING_MATERIAL" : "KNOWLEDGE_BASE",
                            List.of(),
                            null, minutes, List.of(), "AI_PLAN", null, false, null));
            previousSkills.add(com.aicampus.common.evidence.SkillOntology.normalize(skill));
        }
        for (int week = 1; week <= weeks; week++)
            if (!minutesByWeek.containsKey(week))
                throw new IllegalStateException(
                        "AI learning tasks must cover every requested week");
        return List.copyOf(tasks);
    }

    /**
     * A normal plan is allowed to proceed when a retrieved knowledge citation
     * became stale while the plan was being generated. The task remains useful
     * and is marked without a reference; strict validation is still applied when
     * a knowledge action draft is confirmed.
     */
    private List<LearningReference> currentLearningReferences(String skill) {
        List<LearningReference> current = new ArrayList<>();
        for (LearningReference reference : safeList(aiCoachService.learningReferences(skill))) {
            try {
                var document = validateKnowledgeReference(reference);
                current.add(new LearningReference(reference.documentId(), reference.title(), reference.source(),
                        reference.snippet(), document.version()));
            } catch (IllegalArgumentException ignored) {
                // Knowledge indexing can advance between retrieval and plan
                // generation. Omit only the stale citation instead of failing
                // the entire plan; the student can select current material later.
            }
        }
        return List.copyOf(current);
    }

    static List<String> effectiveSkillGaps(
            RecruitmentContextClient.ValidatedContext context, String targetRole) {
        java.util.LinkedHashMap<String, String> gaps = new java.util.LinkedHashMap<>();
        for (String skill : context.missingSkills())
            gaps.put(com.aicampus.common.evidence.SkillOntology.normalize(skill), skill);
        List<String> required =
                context.requiredSkills().isEmpty()
                        ? com.aicampus.common.evidence.SkillOntology.requirements(targetRole)
                        : context.requiredSkills();
        for (String skill : required) {
            boolean supported = false;
            if (context.match() != null
                    && context.match().details() != null
                    && !context.match().details().stale())
                supported =
                        context.match().details().requirements().stream()
                                .anyMatch(
                                        r ->
                                                r.supported()
                                                        && com.aicampus.common.evidence
                                                                .SkillOntology.same(
                                                                skill, r.skill()));
            if (!supported
                    && context.resume() != null
                    && context.resume().structuredDiagnosis() != null
                    && !context.resume().structuredDiagnosis().stale())
                supported =
                        context.resume().structuredDiagnosis().skillEvidence().stream()
                                .anyMatch(
                                        e ->
                                                e.supported()
                                                        && com.aicampus.common.evidence
                                                                .SkillOntology.same(
                                                                skill, e.skill()));
            if (!supported && context.resume() != null)
                supported =
                        safeList(context.resume().projects()).stream()
                                .anyMatch(
                                        project ->
                                                com.aicampus.common.evidence.SkillOntology.mentions(
                                                                project, skill)
                                                        && project.matches(
                                                                "(?s).*(实现|负责|测试|验证|优化|结果|设计|分析|implemented|tested|designed).*"));
            if (!supported)
                gaps.putIfAbsent(
                        com.aicampus.common.evidence.SkillOntology.normalize(skill), skill);
        }
        return List.copyOf(gaps.values());
    }

    static String contextMaterial(RecruitmentContextClient.ValidatedContext context) {
        List<String> material = new ArrayList<>();
        if (context.resume() != null) {
            material.add("简历项目原文：" + String.join("；", safeList(context.resume().projects())));
            if (context.resume().structuredDiagnosis() != null) {
                StructuredResumeDiagnosis diagnosis = context.resume().structuredDiagnosis();
                material.add("简历诊断证据（资料变化=" + diagnosis.stale() + "）：" + diagnosis.skillEvidence());
                material.add("简历表达待补项：" + diagnosis.findings());
            }
        }
        if (context.match() != null && context.match().details() != null)
            material.add(
                    "岗位匹配逐项证据（资料变化="
                            + context.match().details().stale()
                            + "）："
                            + context.match().details().requirements());
        material.add("声明技能、自报完成与材料证据分别记录；引用说明不自动证明掌握。");
        return String.join("；", material);
    }

    private String planFingerprint(
            String studentId,
            RecruitmentContextClient.ValidatedContext context,
            String role,
            int hours,
            int weeks,
            String additional) {
        return fingerprint(
                studentId,
                buildPlanningContext(context, role, hours, additional),
                Integer.toString(weeks),
                Integer.toString(hours),
                context.requiredSkills().toString(),
                context.resume() == null
                        ? ""
                        : String.join("；", safeList(context.resume().projects())),
                context.job() == null ? "" : context.job().jobId(),
                ALGORITHM_VERSION,
                modelKey("learning-plan-v2"));
    }

    private String modelKey(String prompt) {
        return aiCoachService.configuredModel()
                + "|configured="
                + aiCoachService.isModelConfigured()
                + "|structuredAi="
                + aiCoachService.structuredAiEnabled()
                + "|prompt="
                + prompt
                + "|algorithm="
                + ALGORITHM_VERSION;
    }

    private boolean evaluationMatches(InterviewSessionAnswer answer, String input) {
        return answer.evaluation() != null
                && answer.evaluation().analysisMetadata() != null
                && input.equals(answer.evaluation().analysisMetadata().inputFingerprint())
                && "interview-evaluation-v1"
                        .equals(answer.evaluation().analysisMetadata().promptVersion());
    }

    private AnalysisMetadata metadata(String input, boolean mocked, String prompt) {
        return new AnalysisMetadata(
                input,
                ALGORITHM_VERSION,
                mocked ? "rules" : aiCoachService.configuredModel(),
                prompt,
                mocked ? "RULES" : "DASHSCOPE",
                Instant.now());
    }

    private static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = valueOr(part, "").getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private LearningPlan requireLearningPlan(String planId) {
        return learningPlanStore
                .findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Learning plan not found"));
    }

    private InterviewSession requireInterviewSession(String sessionId) {
        return interviewSessionStore
                .findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Interview session not found"));
    }

    private static LearningPlan applySchedule(
            LearningPlan plan, String requestedStartDate, List<String> requestedStudyDays,
            Integer requestedDailyMinutesCap) {
        String startDate = valueOr(requestedStartDate);
        if (startDate == null) startDate = valueOr(plan.startDate());
        List<String> studyDays = usefulStrings(requestedStudyDays);
        if (studyDays.isEmpty()) studyDays = safeList(plan.studyDays());
        if (studyDays.isEmpty()) studyDays = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");
        studyDays = studyDays.stream().map(value -> value.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        List<DayOfWeek> weekdays = new ArrayList<>();
        for (String value : studyDays) {
            try { weekdays.add(DayOfWeek.valueOf(value)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("studyDays must contain valid weekday names"); }
        }
        int cap = requestedDailyMinutesCap == null ? plan.dailyMinutesCap() : requestedDailyMinutesCap;
        if (cap <= 0) cap = Math.max(30, (int) Math.ceil(plan.weeklyHours() * 60d / Math.max(1, studyDays.size())));
        if (cap > 720) throw new IllegalArgumentException("dailyMinutesCap must be between 30 and 720");
        LocalDate first = null;
        try { first = startDate == null ? LocalDate.now() : LocalDate.parse(startDate); }
        catch (DateTimeException ex) { throw new IllegalArgumentException("startDate must be yyyy-MM-dd"); }
        while (!weekdays.contains(first.getDayOfWeek())) {
            first = first.plusDays(1);
        }
        List<LearningTask> ordered = scheduleTaskOrder(safeTasks(plan.tasks()));
        Map<Integer, List<LocalDate>> datesByWeek = new HashMap<>();
        Map<Integer, Integer> minutesByWeek = new HashMap<>();
        int weeklyBudget = Math.max(1, plan.weeklyHours()) * 60;
        for (LearningTask task : ordered) {
            if (task.week() < 1 || task.week() > plan.durationWeeks())
                throw new IllegalArgumentException("Learning task week is outside the requested schedule");
            int minutes = taskMinutes(task);
            if (minutes <= 0) throw new IllegalArgumentException("Every learning task must have estimated minutes");
            if (minutesByWeek.merge(task.week(), minutes, Integer::sum) > weeklyBudget)
                throw new IllegalArgumentException("Week " + task.week() + " exceeds the weekly learning budget");
        }
        for (int week = 1; week <= plan.durationWeeks(); week++) {
            List<LocalDate> dates = new ArrayList<>();
            LocalDate weekStart = first.plusWeeks(week - 1);
            for (int day = 0; day < 7; day++) {
                LocalDate date = weekStart.plusDays(day);
                if (weekdays.contains(date.getDayOfWeek())) dates.add(date);
            }
            datesByWeek.put(week, dates);
        }

        Map<LocalDate, Integer> dailyMinutes = new HashMap<>();
        // Reserve recorded work before placing new tasks. Its dates, IDs, feedback
        // and evidence must survive a schedule change without being split.
        for (LearningTask task : ordered) {
            if (!hasLearningHistory(task) || valueOr(task.taskDate()) == null) continue;
            LocalDate date;
            try { date = LocalDate.parse(task.taskDate()); }
            catch (DateTimeException ex) { throw new IllegalArgumentException("Recorded learning task has an invalid date"); }
            // New preferences constrain future work; they cannot rewrite or
            // invalidate a session already recorded on a previous study day.
            dailyMinutes.merge(date, taskMinutes(task), Integer::sum);
        }
        for (int week = 1; week <= plan.durationWeeks(); week++) {
            final int currentWeek = week;
            int pendingMinutes = ordered.stream().filter(task -> task.week() == currentWeek)
                    .filter(task -> !hasLearningHistory(task) || valueOr(task.taskDate()) == null)
                    .mapToInt(AiCareerCoreService::taskMinutes).sum();
            int capacity = 0;
            for (LocalDate date : datesByWeek.get(week))
                capacity += Math.max(0, cap - dailyMinutes.getOrDefault(date, 0));
            if (pendingMinutes > capacity)
                throw new IllegalArgumentException("Week " + week + " exceeds available study days at the daily learning limit");
        }

        List<LearningTask> scheduled = new ArrayList<>();
        Map<Integer, Integer> dayIndexByWeek = new HashMap<>();
        Map<String, LocalDate> completedDates = new HashMap<>();
        Set<String> taskIds = ordered.stream().map(LearningTask::taskId).collect(java.util.stream.Collectors.toSet());
        for (LearningTask task : ordered) {
            List<LocalDate> dates = datesByWeek.get(task.week());
            int dayIndex = dayIndexByWeek.getOrDefault(task.week(), 0);
            List<String> dependencies = scheduleDependencies(task, taskIds);
            for (String dependency : dependencies) {
                LocalDate prerequisiteDate = completedDates.get(dependency);
                while (dayIndex < dates.size() && dates.get(dayIndex).isBefore(prerequisiteDate)) dayIndex++;
            }
            if (hasLearningHistory(task) && valueOr(task.taskDate()) != null) {
                LocalDate date = LocalDate.parse(task.taskDate());
                for (String dependency : dependencies)
                    if (date.isBefore(completedDates.get(dependency)))
                        throw new IllegalArgumentException("Recorded learning task dependency is scheduled after its work");
                scheduled.add(task);
                completedDates.put(task.taskId(), date);
                dayIndexByWeek.put(task.week(), Math.max(dayIndex, dates.indexOf(date)));
                continue;
            }

            int remaining = taskMinutes(task);
            List<LocalDate> segmentDates = new ArrayList<>();
            List<Integer> segmentMinutes = new ArrayList<>();
            while (remaining > 0 && dayIndex < dates.size()) {
                LocalDate date = dates.get(dayIndex);
                int available = cap - dailyMinutes.getOrDefault(date, 0);
                if (available <= 0 || hasLearningHistory(task) && available < remaining) {
                    dayIndex++;
                    continue;
                }
                int minutes = Math.min(remaining, available);
                segmentDates.add(date);
                segmentMinutes.add(minutes);
                dailyMinutes.merge(date, minutes, Integer::sum);
                remaining -= minutes;
                if (remaining > 0) dayIndex++;
            }
            if (remaining > 0)
                throw new IllegalArgumentException("Week " + task.week()
                        + " has insufficient daily learning capacity for task " + task.taskId());

            String previousSegmentId = null;
            for (int segment = 0; segment < segmentDates.size(); segment++) {
                // Keep the original ID on the last segment: tasks depending on
                // that ID must wait for the entire original exercise to finish.
                String id = task.taskId();
                if (segment < segmentDates.size() - 1) {
                    String baseId = task.taskId() + "-S" + (segment + 1);
                    id = baseId;
                    for (int suffix = 2; !taskIds.add(id); suffix++) id = baseId + "-" + suffix;
                }
                List<String> segmentDependencies = new ArrayList<>(safeList(task.dependencies()));
                if (previousSegmentId != null) segmentDependencies.add(previousSegmentId);
                String title = segmentDates.size() == 1 ? task.title()
                        : task.title() + "（第 " + (segment + 1) + "/" + segmentDates.size() + " 段）";
                int minutes = segmentMinutes.get(segment);
                scheduled.add(new LearningTask(id, task.week(), title, task.description(), task.skillGap(), task.stage(),
                        task.acceptanceCriteria(), task.practiceDeliverable(),
                        segmentDates.size() == 1 ? task.estimatedHours() : (minutes + 59) / 60,
                        task.status(), task.feedback(), task.completedAt(), task.updatedAt(), safeList(task.prerequisites()),
                        safeList(task.references()), task.referenceStatus(), safeList(task.evidence()),
                        segmentDates.get(segment).toString(), minutes, List.copyOf(segmentDependencies), task.source(),
                        task.actualMinutes(), task.delayed() || task.deferredUntil() != null, task.deferredUntil()));
                previousSegmentId = id;
            }
            completedDates.put(task.taskId(), segmentDates.get(segmentDates.size() - 1));
            dayIndexByWeek.put(task.week(), dayIndex);
        }
        scheduled.sort(Comparator.comparing(LearningTask::taskDate));
        return new LearningPlan(plan.planId(), plan.rootPlanId(), plan.studentId(), plan.resumeId(), plan.jobId(),
                plan.matchId(), plan.targetRole(), plan.contextSnapshot(), plan.weeklyHours(), plan.durationWeeks(),
                first.toString(), studyDays, cap, plan.status(), plan.version(), plan.revisionOfPlanId(), scheduled,
                plan.mocked(), plan.createdAt(), plan.updatedAt(), plan.revisionReason(), plan.analysisMetadata());
    }

    private static List<LearningTask> scheduleTaskOrder(List<LearningTask> tasks) {
        Set<String> ids = new HashSet<>();
        for (LearningTask task : tasks)
            if (valueOr(task.taskId()) == null || !ids.add(task.taskId()))
                throw new IllegalArgumentException("Learning task identifiers must be present and unique");
        List<LearningTask> pending = new ArrayList<>(tasks);
        pending.sort(Comparator.comparingInt(LearningTask::week));
        List<LearningTask> ordered = new ArrayList<>();
        Set<String> resolved = new HashSet<>();
        while (!pending.isEmpty()) {
            int week = pending.get(0).week();
            LearningTask next = pending.stream().filter(task -> task.week() == week)
                    .filter(task -> resolved.containsAll(scheduleDependencies(task, ids)))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Learning task dependency order is invalid"));
            pending.remove(next);
            ordered.add(next);
            resolved.add(next.taskId());
        }
        return ordered;
    }

    private static List<String> scheduleDependencies(LearningTask task, Set<String> taskIds) {
        if (!safeList(task.dependencies()).isEmpty()) {
            if (!taskIds.containsAll(task.dependencies()))
                throw new IllegalArgumentException("Learning task dependency is missing");
            return task.dependencies();
        }
        // Older AI plans record skill prerequisites; only actual task IDs form
        // scheduling dependencies. The original prerequisite text is retained.
        return safeList(task.prerequisites()).stream().filter(taskIds::contains).toList();
    }

    private static boolean hasLearningHistory(LearningTask task) {
        return "COMPLETED".equals(task.status()) || "SKIPPED".equals(task.status())
                || "IN_PROGRESS".equals(task.status()) || task.actualMinutes() != null && task.actualMinutes() > 0
                || !safeList(task.evidence()).isEmpty();
    }

    private static boolean hasScheduledSegments(List<LearningTask> tasks) {
        return safeTasks(tasks).stream().anyMatch(task -> valueOr(task.taskDate()) != null
                && safeList(task.dependencies()).stream().anyMatch(id -> id.startsWith(task.taskId() + "-S")));
    }

    private static List<LearningTask> preserveSegmentedPlanWork(
            List<LearningTask> newTasks, List<LearningTask> previousTasks, int weeklyHours, int durationWeeks) {
        Map<String, LearningTask> previousById = new LinkedHashMap<>();
        for (LearningTask task : safeTasks(previousTasks)) previousById.putIfAbsent(task.taskId(), task);
        Set<String> retainedIds = previousById.values().stream()
                .filter(task -> "COMPLETED".equals(task.status())).map(LearningTask::taskId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        // A completed final segment may refer to an earlier segment. Keep that
        // predecessor as well so preserved history never gains a missing dependency.
        List<String> closure = new ArrayList<>(retainedIds);
        for (int index = 0; index < closure.size(); index++) {
            LearningTask task = previousById.get(closure.get(index));
            for (String id : scheduleDependencies(task, previousById.keySet()))
                if (retainedIds.add(id)) closure.add(id);
        }
        List<LearningTask> result = new ArrayList<>();
        for (int week = 1; week <= durationWeeks; week++) {
            final int currentWeek = week;
            List<LearningTask> retained = previousById.values().stream()
                    .filter(task -> task.week() == currentWeek && retainedIds.contains(task.taskId())).toList();
            result.addAll(retained);
            int remaining = weeklyHours * 60 - retained.stream().mapToInt(AiCareerCoreService::taskMinutes).sum();
            if (remaining < 0) throw new IllegalArgumentException("Retained work exceeds the weekly learning budget");
            if (remaining == 0) continue;
            List<LearningTask> candidates = safeTasks(newTasks).stream()
                    .filter(task -> task.week() == currentWeek && !retainedIds.contains(task.taskId())).toList();
            int generatedMinutes = candidates.stream().mapToInt(AiCareerCoreService::taskMinutes).sum();
            int target = Math.min(remaining, generatedMinutes);
            if (candidates.isEmpty() || target < candidates.size())
                throw new IllegalArgumentException("Remaining weekly budget cannot retain every generated learning task");
            int distributable = target - candidates.size();
            int weight = generatedMinutes - candidates.size();
            List<Integer> minutes = new ArrayList<>();
            for (LearningTask candidate : candidates)
                minutes.add(1 + (weight == 0 ? 0
                        : (int) ((long) (taskMinutes(candidate) - 1) * distributable / weight)));
            int leftover = target - minutes.stream().mapToInt(Integer::intValue).sum();
            for (int index = 0; leftover > 0 && index < candidates.size(); index++)
                if (minutes.get(index) < taskMinutes(candidates.get(index))) {
                    minutes.set(index, minutes.get(index) + 1);
                    leftover--;
                }
            for (int index = 0; index < candidates.size(); index++)
                result.add(copyTaskWithMinutes(candidates.get(index), minutes.get(index)));
        }
        return List.copyOf(result);
    }

    private LearningPlan withAnalysisMetadata(
            LearningPlan plan, String inputFingerprint, boolean mocked, String modelVersion) {
        return new LearningPlan(plan.planId(), plan.rootPlanId(), plan.studentId(), plan.resumeId(),
                plan.jobId(), plan.matchId(), plan.targetRole(), plan.contextSnapshot(), plan.weeklyHours(),
                plan.durationWeeks(), plan.startDate(), safeList(plan.studyDays()), plan.dailyMinutesCap(),
                plan.status(), plan.version(), plan.revisionOfPlanId(), safeTasks(plan.tasks()), mocked,
                plan.createdAt(), plan.updatedAt(), plan.revisionReason(),
                metadata(inputFingerprint, mocked, modelVersion));
    }

    private static LearningPlan copyPlan(
            LearningPlan plan, String status, List<LearningTask> tasks, Instant updatedAt) {
        return new LearningPlan(
                plan.planId(),
                plan.rootPlanId(),
                plan.studentId(),
                plan.resumeId(),
                plan.jobId(),
                plan.matchId(),
                plan.targetRole(),
                plan.contextSnapshot(),
                plan.weeklyHours(),
                plan.durationWeeks(),
                plan.startDate(),
                safeList(plan.studyDays()),
                plan.dailyMinutesCap(),
                status,
                plan.version(),
                plan.revisionOfPlanId(),
                List.copyOf(tasks),
                plan.mocked(),
                plan.createdAt(),
                updatedAt,
                plan.revisionReason(),
                plan.analysisMetadata());
    }

    private static String resolveTargetRole(
            String requestedTargetRole, RecruitmentContextClient.ValidatedContext context) {
        String targetRole = valueOr(requestedTargetRole);
        if (targetRole != null) {
            return targetRole;
        }
        if (context.job() != null && valueOr(context.job().title()) != null) {
            return context.job().title().trim();
        }
        throw new IllegalArgumentException("targetRole or jobId is required");
    }

    private static int normalizeWeeklyHours(Integer requested) {
        return normalizeWeeklyHours(requested, DEFAULT_WEEKLY_HOURS);
    }

    private static int normalizeWeeklyHours(Integer requested, int fallback) {
        int value = requested == null ? fallback : requested;
        if (value < 2 || value > 40) {
            throw new IllegalArgumentException("weeklyHours must be between 2 and 40");
        }
        return value;
    }

    private static int normalizeDurationWeeks(Integer requested) {
        return normalizeDurationWeeks(requested, DEFAULT_DURATION_WEEKS);
    }

    private static int normalizeDurationWeeks(Integer requested, int fallback) {
        int value = requested == null ? fallback : requested;
        if (value < 1 || value > 24) {
            throw new IllegalArgumentException("durationWeeks must be between 1 and 24");
        }
        return value;
    }

    private static int normalizeQuestionCount(Integer requested) {
        int value = requested == null ? DEFAULT_INTERVIEW_QUESTION_COUNT : requested;
        if (value < 1 || value > 8) {
            throw new IllegalArgumentException("questionCount must be between 1 and 8");
        }
        return value;
    }

    private static int normalizeListLimit(Integer requested) {
        int value = requested == null ? 20 : requested;
        return Math.max(1, Math.min(value, 100));
    }

    private static String normalizeTaskStatus(String status) {
        String normalized = valueOr(status);
        if (normalized == null) {
            throw new IllegalArgumentException("task status is required");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported learning task status");
        }
        return normalized;
    }

    private static String normalizeFeedback(String feedback) {
        String normalized = valueOr(feedback);
        if (normalized != null && normalized.length() > 2000) {
            throw new IllegalArgumentException("feedback must not exceed 2000 characters");
        }
        return normalized;
    }

    private static String stageForWeek(int week, int durationWeeks) {
        if (week <= Math.max(1, durationWeeks / 3)) {
            return "FOUNDATION";
        }
        if (week <= Math.max(2, (durationWeeks * 2) / 3)) {
            return "PRACTICE";
        }
        return "APPLICATION";
    }

    private static void requireStudentId(String studentId) {
        if (valueOr(studentId) == null) {
            throw new IllegalArgumentException("student identity is required");
        }
    }

    private static void requireOwner(String ownerId, String studentId, String resource) {
        if (studentId == null || !studentId.equals(ownerId)) {
            throw new IllegalArgumentException(resource + " not found");
        }
    }

    private static String requireText(String value, String message) {
        String normalized = valueOr(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String valueOr(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOr(String value, String fallback) {
        String normalized = valueOr(value);
        return normalized == null ? fallback : normalized;
    }

    private static boolean sameTargetRole(String left, String right) {
        String normalizedLeft = valueOr(left);
        String normalizedRight = valueOr(right);
        return normalizedLeft != null
                && normalizedRight != null
                && normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private static boolean sameReference(String left, String right) {
        String normalizedLeft = valueOr(left);
        String normalizedRight = valueOr(right);
        return normalizedLeft == null
                ? normalizedRight == null
                : normalizedLeft.equals(normalizedRight);
    }

    private static String displayList(List<String> values, String fallback) {
        List<String> normalized = usefulStrings(values);
        return normalized.isEmpty() ? fallback : String.join("、", normalized);
    }

    private static List<String> usefulStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                valuesSet -> List.copyOf(valuesSet)));
    }

    private static List<String> safeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("Complete a focused practice session");
        }
        List<String> filtered =
                values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
        return filtered.isEmpty() ? List.of("Complete a focused practice session") : filtered;
    }

    private static List<LearningTask> safeTasks(List<LearningTask> values) {
        return values == null ? List.of() : values;
    }

    private static List<InterviewSessionAnswer> safeAnswers(List<InterviewSessionAnswer> values) {
        return values == null ? List.of() : values;
    }

    private static List<InterviewSessionQuestion> safeQuestions(
            List<InterviewSessionQuestion> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> mergeDistinct(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                valuesSet -> valuesSet.stream().limit(8).toList()));
    }
}
