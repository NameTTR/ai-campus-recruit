package com.aicampus.ai.service.core;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.common.dto.CareerPlanRequest;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.aicampus.common.dto.InterviewQuestionFeedback;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiCareerCoreService {
    private static final int DEFAULT_WEEKLY_HOURS = 6;
    private static final int DEFAULT_DURATION_WEEKS = 8;
    private static final int DEFAULT_INTERVIEW_QUESTION_COUNT = 5;
    private static final Set<String> TASK_STATUSES = Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "SKIPPED");

    private final AiCoachService aiCoachService;
    private final LearningPlanStore learningPlanStore;
    private final InterviewSessionStore interviewSessionStore;
    private final RecruitmentContextClient contextClient;

    public AiCareerCoreService(
            AiCoachService aiCoachService,
            LearningPlanStore learningPlanStore,
            InterviewSessionStore interviewSessionStore,
            RecruitmentContextClient contextClient) {
        this.aiCoachService = aiCoachService;
        this.learningPlanStore = learningPlanStore;
        this.interviewSessionStore = interviewSessionStore;
        this.contextClient = contextClient;
    }

    public LearningPlan createLearningPlan(String studentId, String userRole, LearningPlanCreateRequest request) {
        requireStudentId(studentId);
        String resumeId = valueOr(request == null ? null : request.resumeId());
        String jobId = valueOr(request == null ? null : request.jobId());
        String matchId = valueOr(request == null ? null : request.matchId());
        RecruitmentContextClient.ValidatedContext context = contextClient.validate(
                studentId, resumeId, jobId, matchId, userRole);
        String targetRole = resolveTargetRole(request == null ? null : request.targetRole(), context);
        int weeklyHours = normalizeWeeklyHours(request == null ? null : request.weeklyHours());
        int durationWeeks = normalizeDurationWeeks(request == null ? null : request.durationWeeks());
        LearningPlan plan = generateLearningPlan(
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
        learningPlanStore.save(plan);
        return plan;
    }

    public List<LearningPlan> listLearningPlans(String studentId, Integer limit) {
        requireStudentId(studentId);
        return learningPlanStore.listByStudent(studentId, normalizeListLimit(limit));
    }

    public LearningPlan getLearningPlan(String planId, String studentId) {
        LearningPlan plan = requireLearningPlan(planId);
        requireOwner(plan.studentId(), studentId, "Learning plan");
        return plan;
    }

    public LearningTask updateLearningTask(
            String planId,
            String taskId,
            String studentId,
            LearningTaskUpdateRequest request) {
        LearningPlan plan = getLearningPlan(planId, studentId);
        if (!"ACTIVE".equals(plan.status())) {
            throw new IllegalArgumentException("Only active learning plans can be updated");
        }
        String status = normalizeTaskStatus(request == null ? null : request.status());
        String feedback = normalizeFeedback(request == null ? null : request.feedback());
        Instant now = Instant.now();
        List<LearningTask> tasks = new ArrayList<>();
        LearningTask updatedTask = null;
        for (LearningTask task : safeTasks(plan.tasks())) {
            if (!task.taskId().equals(taskId)) {
                tasks.add(task);
                continue;
            }
            Instant completedAt = "COMPLETED".equals(status)
                    ? (task.completedAt() == null ? now : task.completedAt())
                    : null;
            updatedTask = new LearningTask(
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
                    now);
            tasks.add(updatedTask);
        }
        if (updatedTask == null) {
            throw new IllegalArgumentException("Learning task not found");
        }
        String planStatus = tasks.stream().allMatch(task -> "COMPLETED".equals(task.status()))
                ? "COMPLETED"
                : "ACTIVE";
        learningPlanStore.save(copyPlan(plan, planStatus, tasks, now));
        return updatedTask;
    }

    public LearningPlan replan(
            String planId,
            String studentId,
            String userRole,
            LearningPlanReplanRequest request) {
        LearningPlan previous = getLearningPlan(planId, studentId);
        if (!"ACTIVE".equals(previous.status())) {
            throw new IllegalArgumentException("Only active learning plans can be replanned");
        }
        String reason = valueOr(request == null ? null : request.reason());
        if (reason == null) {
            throw new IllegalArgumentException("replan reason is required");
        }
        RecruitmentContextClient.ValidatedContext context = contextClient.validate(
                studentId, previous.resumeId(), previous.jobId(), previous.matchId(), userRole);
        int weeklyHours = normalizeWeeklyHours(request == null ? null : request.weeklyHours(), previous.weeklyHours());
        int durationWeeks = normalizeDurationWeeks(request == null ? null : request.durationWeeks(), previous.durationWeeks());
        validateReplanBudget(previous.tasks(), weeklyHours, durationWeeks);
        String interviewSummary = selectedInterviewSummary(
                request == null ? null : request.interviewSessionId(),
                studentId,
                previous.targetRole());
        LearningPlan revised = generateLearningPlan(
                studentId,
                previous.resumeId(),
                previous.jobId(),
                previous.matchId(),
                previous.targetRole(),
                weeklyHours,
                durationWeeks,
                context,
                previous,
                interviewSummary == null ? reason : reason + "; " + interviewSummary);
        learningPlanStore.replaceActiveWithRevision(
                previous,
                copyPlan(previous, "SUPERSEDED", previous.tasks(), Instant.now()),
                revised);
        return revised;
    }

    public List<LearningPlan> listLearningPlanVersions(String planId, String studentId) {
        LearningPlan plan = getLearningPlan(planId, studentId);
        return learningPlanStore.listVersions(studentId, plan.rootPlanId());
    }

    public InterviewSession createInterviewSession(
            String studentId,
            String userRole,
            InterviewSessionCreateRequest request) {
        requireStudentId(studentId);
        String resumeId = valueOr(request == null ? null : request.resumeId());
        String jobId = valueOr(request == null ? null : request.jobId());
        String matchId = valueOr(request == null ? null : request.matchId());
        RecruitmentContextClient.ValidatedContext context = contextClient.validate(
                studentId, resumeId, jobId, matchId, userRole);
        String targetRole = resolveTargetRole(request == null ? null : request.targetRole(), context);
        int questionCount = normalizeQuestionCount(request == null ? null : request.questionCount());
        boolean nonTechnicalRole = isNonTechnicalRole(targetRole);
        List<InterviewQuestion> generated = nonTechnicalRole
                ? nonTechnicalInterviewQuestions(targetRole, questionCount)
                : aiCoachService.generateInterviewQuestions(new InterviewQuestionRequest(
                        studentId,
                        resumeId,
                        jobId,
                        targetRole,
                        context.resumeSkills(),
                        questionCount,
                        true,
                        6));
        if (generated == null || generated.size() < questionCount) {
            throw new IllegalArgumentException("Interview question generation did not return enough questions");
        }
        String sessionId = "IS-" + UUID.randomUUID().toString().substring(0, 12);
        boolean mocked = nonTechnicalRole
                || generated.stream().allMatch(question -> question.questionId().startsWith("IQ-RAG-"));
        List<InterviewSessionQuestion> questions = new ArrayList<>();
        for (int index = 0; index < questionCount; index++) {
            InterviewQuestion question = generated.get(index);
            String questionId = sessionId + "-Q" + (index + 1);
            questions.add(new InterviewSessionQuestion(
                    questionId,
                    (index + 1) * 10,
                    questionId,
                    valueOr(question.category(), "general"),
                    valueOr(question.difficulty(), "medium"),
                    requireText(question.question(), "Generated interview question is blank"),
                    safeStrings(question.referencePoints()),
                    false,
                    mocked ? "FALLBACK" : "DASHSCOPE"));
        }
        Instant now = Instant.now();
        InterviewSession session = new InterviewSession(
                sessionId,
                studentId,
                resumeId,
                jobId,
                matchId,
                targetRole,
                context.snapshot(resumeId, jobId, matchId),
                "IN_PROGRESS",
                List.copyOf(questions),
                List.of(),
                null,
                mocked,
                now,
                now,
                null);
        interviewSessionStore.save(session);
        return session;
    }

    public List<InterviewSession> listInterviewSessions(String studentId, Integer limit) {
        requireStudentId(studentId);
        return interviewSessionStore.listByStudent(studentId, normalizeListLimit(limit));
    }

    public InterviewSession getInterviewSession(String sessionId, String studentId) {
        InterviewSession session = requireInterviewSession(sessionId);
        requireOwner(session.studentId(), studentId, "Interview session");
        return session;
    }

    public InterviewSession answerInterviewQuestion(
            String sessionId,
            String questionId,
            String studentId,
            InterviewSessionAnswerRequest request) {
        InterviewSession session = getInterviewSession(sessionId, studentId);
        if (!"IN_PROGRESS".equals(session.status())) {
            throw new IllegalArgumentException("Interview session is already finished");
        }
        String answer = requireText(request == null ? null : request.answer(), "answer is required");
        String requestedQuestionId = requireText(
                request == null ? null : request.questionId(), "questionId is required");
        if (!questionId.equals(requestedQuestionId)) {
            throw new IllegalArgumentException("questionId must match the path");
        }
        if (answer.length() > 8000) {
            throw new IllegalArgumentException("answer must not exceed 8000 characters");
        }
        InterviewSessionAnswer existingAnswer = session.answers().stream()
                .filter(item -> questionId.equals(item.questionId()))
                .findFirst()
                .orElse(null);
        if (existingAnswer != null) {
            if (existingAnswer.answer().equals(answer)) {
                return session;
            }
            throw new IllegalArgumentException("Interview question already has an answer");
        }
        InterviewSessionQuestion expected = nextUnansweredQuestion(session);
        if (expected == null || !expected.questionId().equals(questionId)) {
            throw new IllegalArgumentException("Interview answers must be submitted in question order");
        }
        Instant now = Instant.now();
        List<InterviewSessionAnswer> answers = new ArrayList<>(safeAnswers(session.answers()));
        answers.add(new InterviewSessionAnswer(questionId, answer, now));
        List<InterviewSessionQuestion> questions = new ArrayList<>(safeQuestions(session.questions()));
        if (!expected.followUp()
                && needsFollowUp(answer)
                && questions.stream().noneMatch(question -> question.questionId().equals(expected.questionId() + "-F1"))) {
            questions.add(followUpQuestion(session, expected));
        }
        InterviewSession updated = new InterviewSession(
                session.sessionId(),
                session.studentId(),
                session.resumeId(),
                session.jobId(),
                session.matchId(),
                session.targetRole(),
                session.contextSnapshot(),
                session.status(),
                questions.stream().sorted(Comparator.comparingInt(InterviewSessionQuestion::order)).toList(),
                List.copyOf(answers),
                session.report(),
                session.mocked(),
                session.createdAt(),
                now,
                session.completedAt());
        interviewSessionStore.save(updated);
        return updated;
    }

    public InterviewSessionReport finishInterviewSession(String sessionId, String studentId) {
        InterviewSession session = getInterviewSession(sessionId, studentId);
        if (session.report() != null) {
            return session.report();
        }
        if (!"IN_PROGRESS".equals(session.status()) || nextUnansweredQuestion(session) != null) {
            throw new IllegalArgumentException("All interview questions must be answered before finish");
        }
        Map<String, InterviewSessionAnswer> answers = new HashMap<>();
        for (InterviewSessionAnswer answer : safeAnswers(session.answers())) {
            answers.put(answer.questionId(), answer);
        }
        List<InterviewQuestionFeedback> feedback = new ArrayList<>();
        for (InterviewSessionQuestion question : session.questions()) {
            InterviewSessionAnswer answer = answers.get(question.questionId());
            InterviewFeedback result = aiCoachService.generateInterviewFeedback(new InterviewFeedbackRequest(
                    session.studentId(),
                    question.questionId(),
                    question.question(),
                    answer.answer(),
                    session.targetRole()));
            feedback.add(new InterviewQuestionFeedback(
                    question.questionId(),
                    result.score(),
                    safeStrings(result.strengths()),
                    safeStrings(result.gaps()),
                    safeStrings(result.suggestions()),
                    valueOr(result.summary(), "Interview feedback is unavailable"),
                    result.mocked()));
        }
        InterviewSessionReport report = toReport(session.sessionId(), feedback);
        Instant now = Instant.now();
        InterviewSession completed = new InterviewSession(
                session.sessionId(),
                session.studentId(),
                session.resumeId(),
                session.jobId(),
                session.matchId(),
                session.targetRole(),
                session.contextSnapshot(),
                "COMPLETED",
                session.questions(),
                session.answers(),
                report,
                session.mocked() || report.mocked(),
                session.createdAt(),
                now,
                now);
        interviewSessionStore.save(completed);
        return report;
    }

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
        String planningContext = buildPlanningContext(context, targetRole, weeklyHours, additionalContext);
        CareerPlanResponse generated = aiCoachService.careerPlan(new CareerPlanRequest(
                studentId,
                targetRole,
                context.resumeSkills(),
                context.missingSkills(),
                planningContext,
                durationWeeks));
        if (previous != null && generated.mocked()) {
            throw new IllegalStateException("Unable to generate an AI learning plan revision");
        }
        String planId = "LP-" + UUID.randomUUID().toString().substring(0, 12);
        Instant now = Instant.now();
        List<String> gaps = context.missingSkills().isEmpty()
                ? usefulStrings(generated.skillGaps())
                : context.missingSkills();
        List<LearningTask> tasks = createTasks(
                planId, targetRole, durationWeeks, weeklyHours, gaps, generated.weeklyActions());
        if (previous != null) {
            tasks = preserveCompletedTasks(tasks, previous.tasks(), weeklyHours, durationWeeks);
        }
        LearningPlan plan = new LearningPlan(
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
                now);
        return plan;
    }

    private List<LearningTask> createTasks(
            String planId,
            String targetRole,
            int durationWeeks,
            int weeklyHours,
            List<String> skillGaps,
            List<String> actions) {
        List<String> safeActions = usefulStrings(actions);
        List<String> safeGaps = usefulStrings(skillGaps);
        if (safeGaps.isEmpty()) {
            safeGaps = List.of("项目成果量化与岗位表达");
        }
        List<LearningTask> tasks = new ArrayList<>();
        int firstHours = weeklyHours / 2;
        int secondHours = weeklyHours - firstHours;
        Instant now = Instant.now();
        for (int week = 1; week <= durationWeeks; week++) {
            String gap = safeGaps.get((week - 1) % safeGaps.size());
            String action = safeActions.isEmpty()
                    ? "围绕" + gap + "完成一次有证据的练习"
                    : safeActions.get((week - 1) % safeActions.size());
            tasks.add(new LearningTask(
                    planId + "-W" + week + "-A",
                    week,
                    "第" + week + "周：补齐" + gap + "能力",
                    "面向" + targetRole + "岗位，执行：" + action + "。记录你的方法、取舍和结果。",
                    gap,
                    stageForWeek(week, durationWeeks),
                    "能用自己的语言说明" + gap + "的应用场景，并完成一项限时练习或案例拆解。",
                    "一份练习复盘，包含问题、做法、验证方式和至少一项结果证据。",
                    firstHours,
                    "PENDING",
                    null,
                    null,
                    now));
            tasks.add(new LearningTask(
                    planId + "-W" + week + "-B",
                    week,
                    "第" + week + "周：用" + gap + "产出求职证据",
                    "把本周练习转化为可展示成果，复盘与" + targetRole + "岗位要求的差距：" + gap + "。",
                    gap,
                    stageForWeek(week, durationWeeks),
                    "至少关联一项可核验材料，如数据、测试结果、作品链接、截图或导师反馈。",
                    "一条可放入简历或作品集的成果描述，以及下一周的改进清单。",
                    secondHours,
                    "PENDING",
                    null,
                    null,
                    now));
        }
        return List.copyOf(tasks);
    }

    private List<LearningTask> preserveCompletedTasks(
            List<LearningTask> newTasks,
            List<LearningTask> previousTasks,
            int weeklyHours,
            int durationWeeks) {
        Map<Integer, List<LearningTask>> completedByWeek = new HashMap<>();
        for (LearningTask task : safeTasks(previousTasks)) {
            if ("COMPLETED".equals(task.status())) {
                completedByWeek.computeIfAbsent(task.week(), ignored -> new ArrayList<>()).add(task);
            }
        }
        List<LearningTask> result = new ArrayList<>();
        for (int week = 1; week <= durationWeeks; week++) {
            int currentWeek = week;
            List<LearningTask> completed = completedByWeek.getOrDefault(week, List.of());
            result.addAll(completed);
            int completedHours = completed.stream().mapToInt(LearningTask::estimatedHours).sum();
            int remainingHours = weeklyHours - completedHours;
            if (remainingHours <= 0) {
                continue;
            }
            List<LearningTask> candidates = newTasks.stream()
                    .filter(task -> task.week() == currentWeek)
                    .limit(Math.max(0, 2 - completed.size()))
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("Completed tasks leave no remaining task slot for week " + week);
            }
            for (int index = 0; index < candidates.size(); index++) {
                LearningTask task = candidates.get(index);
                int allocatedHours = remainingHours / candidates.size()
                        + (index < remainingHours % candidates.size() ? 1 : 0);
                result.add(copyTaskWithHours(task, allocatedHours));
            }
        }
        return result.stream()
                .sorted(Comparator.comparingInt(LearningTask::week).thenComparing(LearningTask::taskId))
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

    private InterviewSessionQuestion followUpQuestion(InterviewSession session, InterviewSessionQuestion mainQuestion) {
        String questionId = mainQuestion.questionId() + "-F1";
        return new InterviewSessionQuestion(
                questionId,
                mainQuestion.order() + 5,
                mainQuestion.mainQuestionId(),
                mainQuestion.category(),
                mainQuestion.difficulty(),
                "请补充一个可量化的结果、你做过的取舍，以及验证结果的方式："
                        + mainQuestion.question(),
                List.of("给出可量化结果", "说明关键取舍", "描述验证证据"),
                true,
                "FALLBACK");
    }

    private InterviewSessionReport toReport(String sessionId, List<InterviewQuestionFeedback> feedback) {
        int overallScore = (int) Math.round(feedback.stream().mapToInt(InterviewQuestionFeedback::score).average().orElse(0));
        List<String> strengths = mergeDistinct(feedback.stream()
                .flatMap(item -> safeStrings(item.strengths()).stream())
                .toList());
        List<String> gaps = mergeDistinct(feedback.stream()
                .flatMap(item -> safeStrings(item.gaps()).stream())
                .toList());
        List<String> recommendations = mergeDistinct(feedback.stream()
                .flatMap(item -> safeStrings(item.suggestions()).stream())
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
        sections.add("当前已具备技能：" + displayList(context.resumeSkills(), "暂未提供"));
        sections.add("岗位要求技能：" + displayList(context.requiredSkills(), "暂未提供"));
        sections.add("待补齐技能：" + displayList(context.missingSkills(), "请优先补充可验证成果"));
        sections.add("每周可投入时间：" + weeklyHours + "小时");
        if (valueOr(additionalContext) != null) {
            sections.add("本次调整依据：" + additionalContext.trim());
        }
        return String.join("\n", sections);
    }

    private static void validateReplanBudget(
            List<LearningTask> previousTasks,
            int weeklyHours,
            int durationWeeks) {
        Map<Integer, Integer> completedHoursByWeek = new HashMap<>();
        for (LearningTask task : safeTasks(previousTasks)) {
            if (!"COMPLETED".equals(task.status())) {
                continue;
            }
            if (task.week() > durationWeeks) {
                throw new IllegalArgumentException("Cannot shorten the plan past a completed task in week " + task.week());
            }
            completedHoursByWeek.merge(task.week(), task.estimatedHours(), Integer::sum);
        }
        for (Map.Entry<Integer, Integer> entry : completedHoursByWeek.entrySet()) {
            if (entry.getValue() > weeklyHours) {
                throw new IllegalArgumentException(
                        "weeklyHours is lower than completed work in week " + entry.getKey());
            }
        }
    }

    private static LearningTask copyTaskWithHours(LearningTask task, int estimatedHours) {
        return new LearningTask(
                task.taskId(),
                task.week(),
                task.title(),
                task.description(),
                task.skillGap(),
                task.stage(),
                task.acceptanceCriteria(),
                task.practiceDeliverable(),
                estimatedHours,
                task.status(),
                task.feedback(),
                task.completedAt(),
                task.updatedAt());
    }

    private static boolean isNonTechnicalRole(String targetRole) {
        String role = valueOr(targetRole, "").toLowerCase(Locale.ROOT);
        return role.contains("运营")
                || role.contains("市场")
                || role.contains("销售")
                || role.contains("人力")
                || role.contains("招聘")
                || role.contains("行政")
                || role.contains("教师")
                || role.contains("客服")
                || role.contains("design")
                || role.contains("marketing")
                || role.contains("sales")
                || role.contains("recruit")
                || role.contains("operation");
    }

    private static List<InterviewQuestion> nonTechnicalInterviewQuestions(String targetRole, int questionCount) {
        List<InterviewQuestion> templates = List.of(
                new InterviewQuestion(
                        "IQ-ROLE-001", "经历复盘", "中等",
                        "请讲一次你为" + targetRole + "相关目标制定计划并推进落地的经历。",
                        List.of("说明目标和约束", "讲清自己的具体行动", "用结果或反馈证明成效")),
                new InterviewQuestion(
                        "IQ-ROLE-002", "沟通协作", "中等",
                        "当合作方对" + targetRole + "方案存在分歧时，你会如何沟通并推动共识？",
                        List.of("先理解对方诉求", "提出可比较的方案", "明确责任人与后续节点")),
                new InterviewQuestion(
                        "IQ-ROLE-003", "问题解决", "进阶",
                        "请举例说明你如何根据数据、用户反馈或现场观察发现问题，并调整" + targetRole + "工作。",
                        List.of("交代信息来源", "说明判断和取舍", "给出调整后的结果")),
                new InterviewQuestion(
                        "IQ-ROLE-004", "执行管理", "基础",
                        "面对多项紧急任务时，你会如何安排" + targetRole + "工作的优先级并控制风险？",
                        List.of("说明优先级依据", "拆分可执行步骤", "保留同步和风险预警机制")),
                new InterviewQuestion(
                        "IQ-ROLE-005", "成长动机", "基础",
                        "为什么选择" + targetRole + "方向？你准备如何在入职前三个月证明自己的价值？",
                        List.of("连接个人经历与岗位", "给出可衡量的短期目标", "说明学习与复盘方法")));
        List<InterviewQuestion> questions = new ArrayList<>();
        for (int index = 0; index < questionCount; index++) {
            InterviewQuestion template = templates.get(index % templates.size());
            questions.add(new InterviewQuestion(
                    "IQ-ROLE-" + String.format("%03d", index + 1),
                    template.category(),
                    template.difficulty(),
                    template.question(),
                    template.referencePoints()));
        }
        return questions;
    }

    private String selectedInterviewSummary(String sessionId, String studentId, String targetRole) {
        if (valueOr(sessionId) == null) {
            return null;
        }
        InterviewSession session = getInterviewSession(sessionId, studentId);
        if (session.report() == null || !"COMPLETED".equals(session.status())) {
            throw new IllegalArgumentException("Selected interview session is not completed");
        }
        if (!targetRole.equals(session.targetRole())) {
            throw new IllegalArgumentException("Selected interview session has a different target role");
        }
        return String.join("; ", session.report().recommendations());
    }

    private LearningPlan requireLearningPlan(String planId) {
        return learningPlanStore.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Learning plan not found"));
    }

    private InterviewSession requireInterviewSession(String sessionId) {
        return interviewSessionStore.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Interview session not found"));
    }

    private static LearningPlan copyPlan(LearningPlan plan, String status, List<LearningTask> tasks, Instant updatedAt) {
        return new LearningPlan(
                plan.planId(), plan.rootPlanId(), plan.studentId(), plan.resumeId(), plan.jobId(), plan.matchId(),
                plan.targetRole(), plan.contextSnapshot(), plan.weeklyHours(), plan.durationWeeks(), status, plan.version(),
                plan.revisionOfPlanId(),
                List.copyOf(tasks), plan.mocked(), plan.createdAt(), updatedAt);
    }

    private static String resolveTargetRole(String requestedTargetRole, RecruitmentContextClient.ValidatedContext context) {
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

    private static boolean needsFollowUp(String answer) {
        return answer.trim().length() < 80;
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
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        valuesSet -> List.copyOf(valuesSet)));
    }

    private static List<String> safeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("Complete a focused practice session");
        }
        List<String> filtered = values.stream()
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

    private static List<InterviewSessionQuestion> safeQuestions(List<InterviewSessionQuestion> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> mergeDistinct(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        valuesSet -> valuesSet.stream().limit(8).toList()));
    }
}
