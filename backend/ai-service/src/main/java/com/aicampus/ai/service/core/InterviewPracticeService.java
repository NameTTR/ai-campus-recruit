package com.aicampus.ai.service.core;

import com.aicampus.ai.service.AiCoachService;
import com.aicampus.common.dto.*;
import com.aicampus.common.dto.KnowledgeWorkspaceModels.KnowledgeTopic;
import com.aicampus.common.resume.ResumeWorkspaceModels.Experience;
import com.aicampus.common.resume.ResumeWorkspaceModels.MasterProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Versioned interview workflows reuse the existing session snapshot and optimistic store. */
public final class InterviewPracticeService {
    private static final String RUBRIC = "interview-four-dimensions-v2";
    private static final String PROMPT = "interview-evaluation-context-v2";
    private final AiCoachService coach;
    private final InterviewSessionStore store;
    private final RecruitmentContextClient contexts;
    private final AiCareerCoreService career;
    private final Object[] locks = java.util.stream.IntStream.range(0, 128).mapToObj(i -> new Object()).toArray();
    private final Set<String> inFlightAttempts = ConcurrentHashMap.newKeySet();
    private volatile Function<String, KnowledgeTopic> knowledgeSourceValidator;

    public InterviewPracticeService(AiCoachService coach, InterviewSessionStore store,
            RecruitmentContextClient contexts, AiCareerCoreService career) {
        this.coach = coach; this.store = store; this.contexts = contexts; this.career = career;
    }

    public void setKnowledgeSourceValidator(Function<String, KnowledgeTopic> validator) {
        this.knowledgeSourceValidator = validator;
    }

    private Object lock(String id) { return locks[Math.floorMod(id.hashCode(), locks.length)]; }

    public List<InterviewSourceOption> sources(String student, String role, String resumeId, String jobId, String matchId) {
        validateId(resumeId, "resumeId"); validateId(jobId, "jobId"); validateId(matchId, "matchId");
        RecruitmentContextClient.ValidatedContext context = contexts.validate(student, resumeId, jobId, matchId, role);
        List<InterviewSourceOption> options = new ArrayList<>();
        if (context.job() != null || context.match() != null) {
            String id = context.match() != null ? context.match().matchId() : context.job().jobId();
            String target = context.job() == null ? "岗位匹配练习" : context.job().title();
            options.add(new InterviewSourceOption("JOB", id, target, "围绕岗位要求和简历材料练习",
                    resumeId, jobId, matchId, target, "JOB"));
        }
        MasterProfile profile = contexts.loadMasterProfile(student, role);
        if (profile != null && profile.data() != null)
            for (Experience item : list(profile.data().experiences()))
                if (item.confirmed() && item.title() != null && !item.title().isBlank())
                    options.add(new InterviewSourceOption("PROJECT", "PROFILE:" + item.id(), item.title(),
                            join(item.role(), item.actions(), item.methods(), item.results()), resumeId,
                            jobId, matchId, context.job() == null ? null : context.job().title(), "PROFILE"));
        if (context.resume() != null) {
            List<String> projects = list(context.resume().projects());
            for (int i = 0; i < projects.size(); i++) {
                String text = projects.get(i);
                if (text == null || text.isBlank()) continue;
                options.add(new InterviewSourceOption("PROJECT", "RESUME:" + context.resume().resumeId() + ":" + i,
                        shortText(text), text, context.resume().resumeId(), jobId, matchId, null, "RESUME"));
            }
        }
        for (LearningPlan plan : career.listLearningPlans(student, 100))
            for (LearningTask task : list(plan.tasks()))
                for (LearningEvidence evidence : career.listLearningEvidence(plan.planId(), task.taskId(), student))
                    if (usableEvidence(evidence))
                        options.add(new InterviewSourceOption("PROJECT", "EVIDENCE:" + plan.planId() + ":" + task.taskId() + ":" + evidence.evidenceId(),
                                task.title(), evidence.description(), plan.resumeId(), plan.jobId(), plan.matchId(),
                                plan.targetRole(), "EVIDENCE"));
        if (context.match() != null) {
            List<String> gaps = list(context.match().missingSkills());
            for (int i = 0; i < gaps.size(); i++)
                options.add(new InterviewSourceOption("GAP", "MATCH:" + context.match().matchId() + ":" + i,
                        gaps.get(i), "岗位材料中尚未体现的要求", resumeId, jobId, context.match().matchId(),
                        context.job() == null ? null : context.job().title(), "MATCH"));
        }
        for (InterviewSession session : listSessions(student, 100))
            if (session.report() != null && "FINAL".equals(session.report().reportType())) {
                List<String> gaps = list(session.report().gaps());
                for (int i = 0; i < gaps.size(); i++)
                    options.add(new InterviewSourceOption("GAP", "INTERVIEW:" + session.sessionId() + ":" + i,
                            gaps.get(i), "历史面试薄弱项", session.resumeId(), session.jobId(), session.matchId(),
                            session.targetRole(), "INTERVIEW"));
            }
        return List.copyOf(options);
    }

    public InterviewSession create(String student, String role, InterviewSessionCreateRequest request) {
        return create(student, role, request, null, null);
    }

    public InterviewSession createKnowledgePractice(String student, String role, InterviewSessionCreateRequest request,
            String sourceId, String material, List<InterviewSourceReference> references, List<String> gaps, String stableSessionId) {
        if (material == null || material.isBlank() || references == null || references.isEmpty())
            throw new IllegalArgumentException("Knowledge practice requires authorized source material");
        return create(student, role, request, stableSessionId, new Source(sourceId, material, references, gaps, request.targetRole()));
    }

    private InterviewSession create(String student, String role, InterviewSessionCreateRequest request,
            String stableSessionId, Source sourceSnapshot) {
        if (stableSessionId != null && store.findById(stableSessionId).isPresent())
            return get(stableSessionId, student);
        if (request == null) throw new IllegalArgumentException("Interview request is required");
        String mode = choice(request.mode(), "COACHING", Set.of("COACHING", "MOCK"), "mode");
        String type = choice(request.sourceType(), "JOB", Set.of("JOB", "PROJECT", "GAP"), "sourceType");
        Integer minutes = null;
        if ("MOCK".equals(mode)) {
            minutes = request.timerMinutes() == null ? 20 : request.timerMinutes();
            if (minutes == 0) minutes = null;
            if (minutes != null && (minutes < 5 || minutes > 60))
                throw new IllegalArgumentException("timerMinutes must be 0 (off) or between 5 and 60");
        } else if (request.timerMinutes() != null && request.timerMinutes() != 0)
            throw new IllegalArgumentException("Coaching practice does not use a timer");
        String resumeId = text(request.resumeId()), jobId = text(request.jobId()), matchId = text(request.matchId());
        String sourceId = text(request.sourceId());
        validateId(resumeId, "resumeId"); validateId(jobId, "jobId"); validateId(matchId, "matchId"); validateId(sourceId, "sourceId");
        if ("JOB".equals(type) && sourceId != null && !sourceId.equals(jobId) && !sourceId.equals(matchId)) {
            if (sourceId.startsWith("M")) matchId = sourceId; else jobId = sourceId;
        }
        RecruitmentContextClient.ValidatedContext context = contexts.validate(student, resumeId, jobId, matchId, role);
        if (context.match() != null && (context.resume() == null || context.job() == null)) {
            resumeId = context.match().resumeId(); jobId = context.match().jobId();
            context = contexts.validate(student, resumeId, jobId, matchId, role);
        }
        if ("JOB".equals(type) && sourceId == null)
            sourceId = context.match() != null ? context.match().matchId()
                    : context.job() == null ? null : context.job().jobId();
        Source selected = sourceSnapshot == null ? resolveSource(student, role, type, sourceId, context) : sourceSnapshot;
        String target = text(request.targetRole());
        if (target == null) target = context.job() == null ? null : context.job().title();
        if (target == null) target = text(selected.targetRole());
        if (target == null) throw new IllegalArgumentException("targetRole is required without a selected job");
        if (target.length() > 200) throw new IllegalArgumentException("targetRole must not exceed 200 characters");
        int count = request.questionCount() == null ? 5 : request.questionCount();
        if (count < 1 || count > 8) throw new IllegalArgumentException("questionCount must be between 1 and 8");
        List<String> requirements = "JOB".equals(type) ? list(context.requiredSkills()) : ("GAP".equals(type) ? selected.gaps() : List.of());
        List<String> gaps = "GAP".equals(type) ? selected.gaps()
                : ("JOB".equals(type) ? AiCareerCoreService.effectiveSkillGaps(context, target) : List.of());
        String material = "JOB".equals(type)
                ? join(selected.material(), context.job() == null ? null : context.job().description(), AiCareerCoreService.contextMaterial(context))
                : selected.material();
        validateKnowledgeSources(sourceId, material, selected.references());
        String generationContext = "JOB".equals(type) ? material : scopedGenerationContext(type, sourceId, selected);
        List<InterviewQuestion> generated = coach.generateInterviewQuestions(new InterviewQuestionRequest(
                student, resumeId, jobId, target, "JOB".equals(type) ? list(context.resumeSkills()) : ("GAP".equals(type) ? selected.gaps() : List.of()), count, true, 6,
                generationContext, requirements, gaps));
        validateKnowledgeSources(sourceId, material, selected.references());
        if (generated == null || generated.size() < count)
            throw new IllegalArgumentException("Interview question generation did not return enough questions");
        String id = stableSessionId == null ? "IS-" + UUID.randomUUID().toString().substring(0, 12) : stableSessionId;
        boolean mocked = generated.stream().allMatch(q -> q.questionId().startsWith("IQ-RAG-"));
        List<InterviewSessionQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            InterviewQuestion question = generated.get(i);
            String qid = id + "-Q" + (i + 1);
            questions.add(new InterviewSessionQuestion(qid, (i + 1) * 10, qid,
                    fallback(question.category(), "general"), fallback(question.difficulty(), "medium"),
                    required(question.question(), "Generated question is blank"), list(question.referencePoints()),
                    false, mocked ? "FALLBACK" : "DASHSCOPE", selected.references(), RUBRIC));
        }
        Instant now = Instant.now();
        InterviewTimer timer = new InterviewTimer(now, null, 0, minutes, false, 0, now);
        InterviewSession session = new InterviewSession(id, student, resumeId, jobId, matchId, target,
                context.snapshot(resumeId, jobId, matchId), "IN_PROGRESS", List.copyOf(questions), List.of(), null,
                mocked, now, now, null, mode, type, sourceId, selected.label(), material,
                requirements, gaps, selected.references(), List.of(), timer, null, List.of(), false,
                new AnalysisMetadata(hash(join(student, target, mode, type, sourceId, material, String.valueOf(count), String.valueOf(minutes))),
                        "interview-practice-v2", mocked ? "RULES" : coach.configuredModel(), "interview-generation-context-v3", mocked ? "RULES" : "DASHSCOPE", now), List.of());
        store.save(session);
        return session;
    }

    private Source resolveSource(String student, String role, String type, String id,
            RecruitmentContextClient.ValidatedContext context) {
        List<InterviewSourceReference> refs = new ArrayList<>();
        if ("JOB".equals(type)) {
            if (id != null && (context.job() == null || !id.equals(context.job().jobId()))
                    && (context.match() == null || !id.equals(context.match().matchId())))
                throw new IllegalArgumentException("sourceId does not match the verified job or match");
            if (context.job() != null && text(context.job().description()) != null)
                refs.add(new InterviewSourceReference(context.job().jobId(), "JOB", context.job().description(), "description"));
            if (context.resume() != null)
                for (String project : list(context.resume().projects()))
                    if (text(project) != null) refs.add(new InterviewSourceReference(context.resume().resumeId(), "RESUME", project, "projects"));
            return new Source(context.job() == null ? "通用岗位练习" : context.job().title(),
                    refs.stream().map(InterviewSourceReference::quote).reduce((a,b) -> a + "\n" + b).orElse("通用岗位建议，未选择实际材料"),
                    refs, List.of(), null);
        }
        if (id == null) throw new IllegalArgumentException("Choose a verified project or gap source");
        if ("PROJECT".equals(type) && id.startsWith("PROFILE:")) {
            MasterProfile profile = contexts.loadMasterProfile(student, role);
            Experience item = profile == null || profile.data() == null ? null : list(profile.data().experiences()).stream()
                    .filter(e -> ("PROFILE:" + e.id()).equals(id) && e.confirmed()).findFirst().orElse(null);
            if (item == null) throw new IllegalArgumentException("Confirmed project source was not found");
            for (String value : Arrays.asList(item.title(), item.role(), item.actions(), item.methods(), item.results()))
                if (text(value) != null) refs.add(new InterviewSourceReference(id, "PROFILE", value, "experiences/" + item.id()));
            return new Source(item.title(), join(item.title(), item.role(), item.actions(), item.methods(), item.results()), refs, List.of(), null);
        }
        if ("PROJECT".equals(type) && id.startsWith("RESUME:") && context.resume() != null) {
            String prefix = "RESUME:" + context.resume().resumeId() + ":";
            if (id.startsWith(prefix)) {
                int i = index(id.substring(prefix.length()), list(context.resume().projects()).size());
                String value = context.resume().projects().get(i);
                refs.add(new InterviewSourceReference(id, "RESUME", value, "projects/" + i));
                return new Source(shortText(value), value, refs, List.of(), null);
            }
        }
        if ("PROJECT".equals(type) && id.startsWith("EVIDENCE:")) {
            String[] parts = id.split(":", 4);
            if (parts.length != 4) throw new IllegalArgumentException("Invalid evidence source");
            LearningPlan plan = career.getLearningPlan(parts[1], student);
            LearningTask task = list(plan.tasks()).stream().filter(t -> parts[2].equals(t.taskId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Learning task source was not found in the owned plan"));
            LearningEvidence evidence = career.listLearningEvidence(parts[1], parts[2], student).stream()
                    .filter(e -> parts[3].equals(e.evidenceId()) && usableEvidence(e)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Only confirmed evaluated learning evidence can be selected"));
            refs.addAll(career.learningTaskSourceReferences(parts[1], parts[2], student));
            refs.add(new InterviewSourceReference(id, "LEARNING_EVIDENCE", evidence.description(), "description"));
            for (String link : list(evidence.links()))
                refs.add(new InterviewSourceReference(id, "LEARNING_EVIDENCE", link, "links"));
            if (text(task.acceptanceCriteria()) != null)
                refs.add(new InterviewSourceReference(id, "LEARNING_TASK", task.acceptanceCriteria(), "acceptanceCriteria"));
            if (text(task.description()) != null)
                refs.add(new InterviewSourceReference(id, "LEARNING_TASK", task.description(), "description"));
            if (text(task.skillGap()) != null)
                refs.add(new InterviewSourceReference(id, "LEARNING_TASK", task.skillGap(), "skillGap"));
            if (evidence.evaluation() != null) {
                if (text(evidence.evaluation().conclusion()) != null)
                    refs.add(new InterviewSourceReference(id, "LEARNING_EVALUATION", evidence.evaluation().conclusion(), "evaluation/conclusion"));
                for (String value : list(evidence.evaluation().strengths()))
                    refs.add(new InterviewSourceReference(id, "LEARNING_EVALUATION", value, "evaluation/strengths"));
                for (String value : list(evidence.evaluation().gaps()))
                    refs.add(new InterviewSourceReference(id, "LEARNING_EVALUATION", value, "evaluation/gaps"));
            }
            String evaluation = evidence.evaluation() == null ? "" : join(evidence.evaluation().conclusion(),
                    String.join("；", list(evidence.evaluation().strengths())), String.join("；", list(evidence.evaluation().gaps())));
            return new Source(task.title(), join(evidence.description(), String.join("\n", list(evidence.links())),
                    task.description(), task.acceptanceCriteria(), task.skillGap(), evaluation), refs, list(evidence.evaluation().gaps()), plan.targetRole());
        }
        if ("GAP".equals(type) && id.startsWith("MATCH:") && context.match() != null) {
            String prefix = "MATCH:" + context.match().matchId() + ":";
            if (id.startsWith(prefix)) {
                int i = index(id.substring(prefix.length()), list(context.match().missingSkills()).size());
                String gap = context.match().missingSkills().get(i);
                refs.add(new InterviewSourceReference(id, "MATCH_GAP", gap, "missingSkills/" + i));
                return new Source(gap, "岗位材料中尚未体现：" + gap, refs, List.of(gap), null);
            }
        }
        if ("GAP".equals(type) && id.startsWith("INTERVIEW:")) {
            int separator = id.lastIndexOf(':');
            if (separator <= 10 || separator == id.length() - 1)
                throw new IllegalArgumentException("Invalid interview gap source");
            InterviewSession previous = get(id.substring(10, separator), student);
            if (previous.report() == null || !"FINAL".equals(previous.report().reportType()))
                throw new IllegalArgumentException("Only completed interview gaps can be selected");
            String gap = previous.report().gaps().get(index(id.substring(separator + 1), previous.report().gaps().size()));
            refs.add(new InterviewSourceReference(id, "INTERVIEW_GAP", gap, "report/gaps"));
            refs.addAll(previous.sourceReferences().stream().filter(InterviewPracticeService::knowledgeReference).toList());
            return new Source(gap, gap, refs, List.of(gap), previous.targetRole());
        }
        throw new IllegalArgumentException("Source was not found in authorized materials");
    }

    public InterviewSession get(String id, String student) {
        InterviewSession session = store.findById(id).orElseThrow(() -> new IllegalArgumentException("Interview session not found"));
        if (!Objects.equals(student, session.studentId())) throw new IllegalArgumentException("Interview session is not owned by the current student");
        validateKnowledgeSources(session);
        if (session.attempts().isEmpty() && !list(session.answers()).isEmpty()) {
            List<InterviewAnswerAttempt> attempts = session.answers().stream().map(a -> new InterviewAnswerAttempt(
                    "IA-LEGACY-" + a.questionId(), a.questionId(), 1, a.answer(), a.answeredAt(), a.evaluationStatus(),
                    a.evaluation(), a.evaluationError(), hash(a.answer()), true, a.answeredAt(), "Historical saved answer")).toList();
            InterviewSession migrated = copy(session, session.questions(), session.answers(), attempts, session.report(),
                    session.partialReport(), session.timer(), session.actionPreviews(), session.status());
            if (store.replace(session, migrated)) return get(id, student);
            return get(id, student);
        }
        if ("IN_PROGRESS".equals(session.status()) && session.attempts().stream()
                .anyMatch(a -> "EVALUATING".equals(a.evaluationStatus()) && !inFlightAttempts.contains(a.attemptId()))) {
            List<InterviewAnswerAttempt> attempts = session.attempts().stream()
                    .map(a -> "EVALUATING".equals(a.evaluationStatus()) && !inFlightAttempts.contains(a.attemptId())
                            ? new InterviewAnswerAttempt(a.attemptId(), a.questionId(), a.attemptNo(), a.answer(),
                            a.submittedAt(), "FAILED", a.evaluation(), "评价执行已中断，回答已保存，可重试。",
                            a.inputFingerprint(), a.selectedForReport(), a.selectedAt(), a.selectionReason()) : a).toList();
            InterviewSession recovered = copy(session, session.questions(), selectedAnswers(attempts), attempts,
                    session.report(), session.partialReport(), session.timer(), session.actionPreviews(), session.status());
            if (store.replace(session, recovered)) return recovered;
            return get(id, student);
        }
        return session;
    }

    public List<InterviewSession> listSessions(String student, Integer limit) {
        int count = limit == null ? 30 : Math.max(1, Math.min(limit, 100));
        return store.listByStudent(student, count).stream().map(s -> accessibleSession(s.sessionId(), student))
                .flatMap(Optional::stream).toList();
    }

    public InterviewSession answer(String id, String qid, String student, InterviewSessionAnswerRequest request, boolean allowRetry) {
        String value = required(request == null ? null : request.answer(), "answer is required");
        if (request == null || !qid.equals(request.questionId())) throw new IllegalArgumentException("questionId must match the path");
        if (value.length() > 8000) throw new IllegalArgumentException("answer must not exceed 8000 characters");
        synchronized (lock(id)) {
            return update(id, student, s -> {
                requireActive(s);
                if (s.timer() != null && s.timer().pausedAt() != null)
                    throw new IllegalArgumentException("Resume the interview before answering");
                InterviewSessionQuestion requested = question(s, qid);
                if ("MOCK".equals(s.mode()) && requested.followUp())
                    throw new IllegalArgumentException("Report follow-ups are reviewed in a new coaching practice");
                List<InterviewAnswerAttempt> same = s.attempts().stream().filter(a -> qid.equals(a.questionId())).toList();
                for (InterviewAnswerAttempt existing : same) if (value.equals(existing.answer())) return s;
                if (!same.isEmpty() && (!allowRetry || "MOCK".equals(s.mode())))
                    throw new IllegalArgumentException("Interview question already has an answer");
                if (same.isEmpty()) {
                    Set<String> answered = new HashSet<>(); s.answers().forEach(a -> answered.add(a.questionId()));
                    InterviewSessionQuestion next = s.questions().stream().filter(q -> !answered.contains(q.questionId()))
                            .filter(q -> !"MOCK".equals(s.mode()) || !q.followUp())
                            .min(Comparator.comparingInt(InterviewSessionQuestion::order)).orElse(null);
                    if (next == null || !qid.equals(next.questionId()))
                        throw new IllegalArgumentException("Interview answers must be submitted in question order");
                }
                Instant now = Instant.now();
                List<InterviewAnswerAttempt> attempts = new ArrayList<>(s.attempts());
                attempts.add(new InterviewAnswerAttempt("IA-" + UUID.randomUUID(), qid, same.size() + 1, value, now,
                        "PENDING", null, null, hash(value), same.isEmpty(), same.isEmpty() ? now : null,
                        same.isEmpty() ? "首次回答" : null));
                List<InterviewSessionAnswer> answers = new ArrayList<>(s.answers());
                if (same.isEmpty()) answers.add(new InterviewSessionAnswer(qid, value, now));
                return copy(s, s.questions(), answers, attempts, null, null, s.timer(), s.actionPreviews(), s.status());
            });
        }
    }

    public InterviewEvaluationResponse evaluate(String id, String qid, String attemptId, String student, boolean reportEvaluation) {
        synchronized (lock(id)) {
            InterviewSession session = get(id, student);
            if ("MOCK".equals(session.mode()) && liveMock(session) && !reportEvaluation)
                throw new IllegalArgumentException("Mock feedback is available after a report is requested");
            InterviewAnswerAttempt attempt = attempt(session, qid, attemptId);
            if ("SUCCEEDED".equals(attempt.evaluationStatus())
                    && (!"IN_PROGRESS".equals(session.status()) || attempt.evaluation() == null
                    || !attempt.evaluation().mocked() || !coach.structuredAiEnabled() || !coach.isModelConfigured()))
                return evaluationResponse(session, attempt);
            if (!"IN_PROGRESS".equals(session.status())) throw new IllegalArgumentException("Interview is already finished");
            if ("EVALUATING".equals(attempt.evaluationStatus()) && inFlightAttempts.contains(attempt.attemptId()))
                return evaluationResponse(session, attempt);
            InterviewSessionQuestion question = question(session, qid);
            String input = hash(join(student, id, qid, attempt.answer(), session.sourceMaterial(), question.referencePoints().toString(),
                    RUBRIC, PROMPT, coach.configuredModel(), String.valueOf(coach.isModelConfigured()), String.valueOf(coach.structuredAiEnabled())));
            inFlightAttempts.add(attempt.attemptId());
            try {
                updateAttempt(id, student, attempt.attemptId(), "EVALUATING", null, null, input, null);
                InterviewQuestionFeedback feedback = null; String error = null;
                try {
                    InterviewFeedback result = coach.evaluateSavedAnswer(new InterviewFeedbackRequest(student, qid,
                            question.question(), attempt.answer(), session.targetRole(),
                            join(session.sourceMaterial(), "岗位要求=" + session.sourceRequirements(), "能力缺口=" + session.sourceGaps())), question.referencePoints());
                    if (list(result.evidence()).isEmpty()) throw new IllegalArgumentException("Evaluation must cite the saved answer");
                    for (InterviewEvidenceNote note : result.evidence())
                        if (text(note.quote()) == null || !attempt.answer().contains(note.quote())
                                || !Set.of("SUPPORTED", "INCORRECT", "INSUFFICIENT_EVIDENCE").contains(note.type()))
                            throw new IllegalArgumentException("Evaluation quote or finding type is invalid");
                    if (list(result.dimensions()).size() != 4) throw new IllegalArgumentException("Evaluation requires four fixed dimensions");
                    feedback = new InterviewQuestionFeedback(qid, result.score(), list(result.strengths()), list(result.gaps()),
                            list(result.suggestions()), result.summary(), result.mocked(), result.dimensions(), result.evidence(), RUBRIC,
                            result.followUpQuestion(), new AnalysisMetadata(input, "interview-practice-v2",
                            result.mocked() ? "RULES" : coach.configuredModel(), PROMPT, result.mocked() ? "RULES" : "DASHSCOPE", Instant.now()), Instant.now());
                } catch (RuntimeException ex) {
                    error = "模型评价暂时不可用，回答已保存。以下为演示规则反馈，可重试正式评价。";
                    InterviewFeedback fallback = coach.ruleInterviewFeedback(new InterviewFeedbackRequest(student, qid,
                            question.question(), attempt.answer(), session.targetRole()), question.referencePoints());
                    feedback = new InterviewQuestionFeedback(qid, fallback.score(), fallback.strengths(), fallback.gaps(), fallback.suggestions(),
                            fallback.summary(), true, fallback.dimensions(), fallback.evidence(), RUBRIC, null,
                            new AnalysisMetadata(input, "interview-practice-v2", "RULES", PROMPT, "RULES", Instant.now()), Instant.now());
                }
                validateKnowledgeSources(session);
                InterviewSessionQuestion followUp = null;
                if (error == null && feedback != null && (!reportEvaluation || "MOCK".equals(session.mode())) && !question.followUp()
                        && text(feedback.followUpQuestion()) != null && session.questions().stream().noneMatch(q -> q.followUp() && q.mainQuestionId().equals(qid))) {
                    followUp = new InterviewSessionQuestion(qid + "-F1", question.order() + 1, qid, "follow-up", question.difficulty(),
                            feedback.followUpQuestion(), List.of("个人职责", "验证过程", "实际结果"), true,
                            feedback.mocked() ? "FALLBACK" : "DASHSCOPE", question.sourceReferences(), RUBRIC);
                }
                InterviewSession saved = updateAttempt(id, student, attempt.attemptId(), error != null ? "FAILED" : "SUCCEEDED",
                        feedback, error, input, followUp);
                return evaluationResponse(saved, attempt(saved, qid, attempt.attemptId()));
            } finally {
                inFlightAttempts.remove(attempt.attemptId());
            }
        }
    }

    private InterviewSession updateAttempt(String id, String student, String aid, String status,
            InterviewQuestionFeedback feedback, String error, String input, InterviewSessionQuestion followUp) {
        return update(id, student, s -> {
            List<InterviewAnswerAttempt> attempts = s.attempts().stream().map(a -> a.attemptId().equals(aid)
                    ? new InterviewAnswerAttempt(a.attemptId(), a.questionId(), a.attemptNo(), a.answer(), a.submittedAt(),
                    status, feedback, error, input, a.selectedForReport(), a.selectedAt(), a.selectionReason()) : a).toList();
            List<InterviewSessionQuestion> questions = new ArrayList<>(s.questions());
            if (followUp != null && questions.stream().noneMatch(q -> q.questionId().equals(followUp.questionId()))) questions.add(followUp);
            questions.sort(Comparator.comparingInt(InterviewSessionQuestion::order));
            return copy(s, questions, selectedAnswers(attempts), attempts, null, null, s.timer(), s.actionPreviews(), s.status());
        });
    }

    public InterviewSession selectAttempt(String id, String qid, String aid, String student, String reason) {
        synchronized (lock(id)) {
            return update(id, student, s -> {
                requireActive(s);
                if (!"COACHING".equals(s.mode())) throw new IllegalArgumentException("Mock answers cannot be replaced");
                InterviewAnswerAttempt selected = attempt(s, qid, aid);
                if (!"SUCCEEDED".equals(selected.evaluationStatus())) throw new IllegalArgumentException("Evaluate this attempt before adopting it");
                if (selected.selectedForReport()) return s;
                Instant now = Instant.now();
                List<InterviewAnswerAttempt> attempts = s.attempts().stream().map(a -> qid.equals(a.questionId())
                        ? new InterviewAnswerAttempt(a.attemptId(), a.questionId(), a.attemptNo(), a.answer(), a.submittedAt(),
                        a.evaluationStatus(), a.evaluation(), a.evaluationError(), a.inputFingerprint(), a.attemptId().equals(aid),
                        a.attemptId().equals(aid) ? now : a.selectedAt(), a.attemptId().equals(aid) ? fallback(reason, "学生选择采用本次回答") : a.selectionReason()) : a).toList();
                return copy(s, s.questions(), selectedAnswers(attempts), attempts, null, null, s.timer(), s.actionPreviews(), s.status());
            });
        }
    }

    public InterviewSession pause(String id, String student, boolean resume) {
        synchronized (lock(id)) {
            return update(id, student, s -> {
                requireActive(s);
                Instant now = Instant.now(); InterviewTimer timer = elapsed(s.timer(), now);
                if (timer == null) timer = new InterviewTimer(s.createdAt(), null, 0, null, false, 0, now);
                if (resume) {
                    if (timer.pausedAt() == null) return s;
                    timer = new InterviewTimer(timer.startedAt(), null, timer.accumulatedSeconds(), timer.timerMinutes(), timer.timeoutReached(),
                            timer.pausedSeconds() + Math.max(0, Duration.between(timer.pausedAt(), now).toSeconds()), now);
                } else {
                    if (timer.pausedAt() != null) return s;
                    timer = new InterviewTimer(timer.startedAt(), now, timer.accumulatedSeconds(), timer.timerMinutes(), timer.timeoutReached(), timer.pausedSeconds(), null);
                }
                return copy(s, s.questions(), s.answers(), s.attempts(), s.report(), resume ? null : s.partialReport(), timer, s.actionPreviews(), s.status());
            });
        }
    }

    public InterviewSessionReport report(String id, String student, boolean partial) {
        synchronized (lock(id)) {
            InterviewSession session = get(id, student);
            if (session.report() != null) return session.report();
            if (partial && session.partialReport() != null) return session.partialReport();
            List<String> unanswered = unanswered(session);
            if (!partial && !unanswered.isEmpty()) throw new IllegalArgumentException("All interview questions must be answered before finish");
            session = update(id, student, s -> {
                Instant requestedAt = Instant.now();
                InterviewTimer timer = elapsed(s.timer(), requestedAt);
                if (timer == null || timer.runningSince() == null) return s;
                InterviewTimer frozen = new InterviewTimer(timer.startedAt(), requestedAt, timer.accumulatedSeconds(),
                        timer.timerMinutes(), timer.timeoutReached(), timer.pausedSeconds(), null);
                return copy(s, s.questions(), s.answers(), s.attempts(), s.report(), s.partialReport(),
                        frozen, s.actionPreviews(), s.status());
            });
            for (InterviewAnswerAttempt answer : session.attempts().stream().filter(InterviewAnswerAttempt::selectedForReport).toList()) {
                InterviewEvaluationResponse result = evaluate(id, answer.questionId(), answer.attemptId(), student, true);
                if (!"SUCCEEDED".equals(result.status())) throw new IllegalStateException("Evaluation is unavailable; saved answers can be retried");
            }
            session = get(id, student);
            List<InterviewAnswerAttempt> selected = session.attempts().stream().filter(InterviewAnswerAttempt::selectedForReport).toList();
            List<InterviewQuestionFeedback> feedback = selected.stream().map(InterviewAnswerAttempt::evaluation).filter(Objects::nonNull).toList();
            int score = (int) Math.round(feedback.stream().mapToInt(InterviewQuestionFeedback::score).average().orElse(0));
            List<String> strengths = feedback.stream().flatMap(f -> list(f.strengths()).stream()).distinct().limit(6).toList();
            List<String> gaps = feedback.stream().flatMap(f -> list(f.gaps()).stream()).distinct().limit(6).toList();
            List<String> recommendations = feedback.stream().flatMap(f -> list(f.suggestions()).stream()).distinct().limit(6).toList();
            String target = session.targetRole(), mode = session.mode();
            List<String> comparable = partial ? List.of() : listSessions(student, 100).stream()
                    .filter(s -> !id.equals(s.sessionId()) && s.report() != null && "FINAL".equals(s.report().reportType())
                            && "COMPLETED".equals(s.status()) && RUBRIC.equals(s.report().rubricVersion())
                            && mode.equals(s.mode()) && normalize(target).equals(normalize(s.targetRole()))
                            && Objects.equals(s.jobId(), get(id, student).jobId())
                            && (!"MOCK".equals(mode) || (!s.feedbackViewedAfterPartial() && !get(id, student).feedbackViewedAfterPartial())))
                    .map(InterviewSession::sessionId).toList();
            List<InterviewAttemptComparison> comparisons = new ArrayList<>();
            for (InterviewAnswerAttempt answer : selected) {
                InterviewAnswerAttempt original = session.attempts().stream().filter(a -> a.questionId().equals(answer.questionId()) && a.attemptNo() == 1).findFirst().orElse(answer);
                if (!original.attemptId().equals(answer.attemptId())) {
                    Set<String> before = new HashSet<>(original.evaluation() == null ? List.of() : list(original.evaluation().gaps()));
                    Set<String> after = new HashSet<>(answer.evaluation() == null ? List.of() : list(answer.evaluation().gaps()));
                    List<String> improvement = before.stream().filter(v -> !after.contains(v)).sorted().toList();
                    comparisons.add(new InterviewAttemptComparison(answer.questionId(), original.attemptId(), answer.attemptId(),
                            improvement, List.copyOf(after), "辅导后同题重答；需要通过新题检查能否独立应用"));
                }
            }
            InterviewTimer timer = elapsed(session.timer(), Instant.now());
            if (timer != null && timer.runningSince() != null)
                timer = new InterviewTimer(timer.startedAt(), Instant.now(), timer.accumulatedSeconds(), timer.timerMinutes(), timer.timeoutReached(), timer.pausedSeconds(), null);
            InterviewSessionReport report = new InterviewSessionReport(id, score, strengths, gaps, recommendations, feedback,
                    Instant.now(), feedback.stream().anyMatch(InterviewQuestionFeedback::mocked), RUBRIC,
                    partial ? "阶段报告不参与历史成绩比较" : "仅比较相同岗位、模式和评价版本的完整报告", comparable,
                    "本次题目难度：" + session.questions().stream().map(InterviewSessionQuestion::difficulty).distinct().reduce((a,b) -> a + "、" + b).orElse("未知") + "；不同题目的分数仅供练习参考",
                    partial ? "PARTIAL" : "FINAL", selected.size() + "/" + session.questions().stream()
                    .filter(q -> !"MOCK".equals(mode) || !q.followUp()).count(), unanswered(session), selected,
                    comparisons, actions(session, gaps));
            InterviewTimer finalTimer = timer;
            update(id, student, s -> copy(s, s.questions(), s.answers(), s.attempts(), partial ? null : report,
                    partial ? report : s.partialReport(), finalTimer, s.actionPreviews(), partial ? "IN_PROGRESS" : "COMPLETED"));
            return report;
        }
    }

    public List<InterviewNextAction> nextActions(String id, String student) {
        InterviewSession session = get(id, student);
        InterviewSessionReport report = session.report() == null ? session.partialReport() : session.report();
        if (report == null) throw new IllegalArgumentException("Generate a report before choosing next actions");
        return actions(session, report.gaps());
    }

    private List<InterviewNextAction> actions(InterviewSession session, List<String> gaps) {
        List<InterviewNextAction> result = new ArrayList<>();
        List<String> top = list(gaps).stream().limit(3).toList();
        for (int i = 0; i < top.size(); i++) {
            String gap = top.get(i);
            List<InterviewSourceReference> refs = new ArrayList<>(session.sourceReferences());
            refs.add(new InterviewSourceReference(session.sessionId(), "INTERVIEW_GAP", gap, "report/gaps/" + i));
            result.add(new InterviewNextAction("PRACTICE-" + i, "PRACTICE", "再练一道同类题", gap, gap, 15, refs));
            result.add(new InterviewNextAction("KNOWLEDGE-" + i, "KNOWLEDGE", "查看相关资料", gap, gap, 10, refs));
            result.add(new InterviewNextAction("MATERIAL-" + i, "MATERIAL", "补充项目材料", gap, gap, 15, refs));
            result.add(new InterviewNextAction("LEARNING-" + i, "LEARNING", "调整学习计划", gap, gap, 30, refs));
        }
        return List.copyOf(result);
    }

    public InterviewActionPreview previewAction(String id, String student, String actionId, String planId) {
        synchronized (lock(id)) {
            InterviewNextAction action = nextActions(id, student).stream().filter(a -> a.actionId().equals(actionId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Next action was not found"));
            if ("LEARNING".equals(action.type())) {
                if (text(planId) == null) throw new IllegalArgumentException("Choose an active learning plan");
                LearningPlan plan = career.getLearningPlan(planId, student);
                if (!"ACTIVE".equals(plan.status())) throw new IllegalArgumentException("Only an active learning plan can be adjusted");
            }
            InterviewSession s = get(id, student);
            String fingerprint = reportFingerprint(s.report() == null ? s.partialReport() : s.report());
            for (InterviewActionPreview previous : s.actionPreviews())
                if (actionId.equals(previous.actionId()) && Objects.equals(planId, previous.planId())
                        && fingerprint.equals(previous.reportFingerprint()) && action.skill().equals(previous.skillGap())) return previous;
            InterviewActionPreview preview = new InterviewActionPreview("IP-" + UUID.randomUUID(), actionId, action.type(),
                    action.title(), action.description(), action.estimatedMinutes(),
                    "PRACTICE".equals(action.type()) ? "确认后创建一道新题，保留原面试记录"
                            : "LEARNING".equals(action.type()) ? "确认后生成学习计划草稿，原计划保持有效"
                            : "仅打开资料或编辑入口，不修改个人资料", "DRAFT", planId, null, null, action.sourceReferences(), Instant.now(),
                    fingerprint, action.skill());
            update(id, student, current -> {
                List<InterviewActionPreview> previews = new ArrayList<>(current.actionPreviews()); previews.add(preview);
                return copy(current, current.questions(), current.answers(), current.attempts(), current.report(), current.partialReport(),
                        current.timer(), previews, current.status());
            });
            return preview;
        }
    }

    public InterviewActionPreview confirmAction(String id, String student, String previewId) {
        synchronized (lock(id)) {
            InterviewSession session = get(id, student);
            InterviewActionPreview preview = session.actionPreviews().stream().filter(p -> p.previewId().equals(previewId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Action preview was not found"));
            if ("CONFIRMED".equals(preview.status())) return preview;
            InterviewSessionReport currentReport = session.report() == null ? session.partialReport() : session.report();
            if (currentReport == null || !reportFingerprint(currentReport).equals(preview.reportFingerprint()))
                throw new IllegalArgumentException("Action preview is outdated; generate or open the current report and preview it again");
            int i = index(preview.actionId().substring(preview.actionId().lastIndexOf('-') + 1), list(currentReport.gaps()).size());
            if (!Objects.equals(preview.skillGap(), currentReport.gaps().get(i)))
                throw new IllegalArgumentException("Action preview is outdated; preview the current gap again");
            String newSession = null, newPlan = null;
            if ("PRACTICE".equals(preview.type())) {
                if (session.report() == null) throw new IllegalArgumentException("Finish the interview before creating a gap practice");
                String stableSessionId = "IS-ACTION-" + hash(join(student, id, preview.previewId())).substring(0, 24);
                Source sourceSnapshot = new Source(preview.skillGap(), preview.skillGap(), list(preview.sourceReferences()).stream()
                        .filter(r -> knowledgeReference(r)
                                || "INTERVIEW_GAP".equals(r.kind()) && preview.skillGap().equals(r.quote())).toList(),
                        List.of(preview.skillGap()), session.targetRole());
                newSession = create(student, "STUDENT", new InterviewSessionCreateRequest(student, session.resumeId(), session.jobId(),
                        session.matchId(), session.targetRole(), 1, "COACHING", "GAP", "INTERVIEW:" + id + ":" + i, null),
                        stableSessionId, sourceSnapshot).sessionId();
            } else if ("LEARNING".equals(preview.type())) {
                if (session.report() == null) throw new IllegalArgumentException("Finish the interview before replanning");
                newPlan = career.replan(preview.planId(), student, "STUDENT", new LearningPlanReplanRequest(
                        preview.description(), null, null, id, true)).planId();
            }
            InterviewActionPreview confirmed = new InterviewActionPreview(preview.previewId(), preview.actionId(), preview.type(), preview.title(),
                    preview.description(), preview.estimatedMinutes(), preview.impact(), "CONFIRMED", preview.planId(), newSession, newPlan,
                    preview.sourceReferences(), preview.createdAt(), preview.reportFingerprint(), preview.skillGap());
            update(id, student, s -> copy(s, s.questions(), s.answers(), s.attempts(), s.report(), s.partialReport(), s.timer(),
                    s.actionPreviews().stream().map(p -> p.previewId().equals(previewId) ? confirmed : p).toList(), s.status()));
            return confirmed;
        }
    }

    public InterviewResumeCandidate resumeCandidate(String id, String student, String qid, String aid) {
        InterviewSession session = get(id, student);
        if (liveMock(session)) throw new IllegalArgumentException("Generate a mock report before preparing resume material");
        InterviewAnswerAttempt answer = attempt(session, qid, aid);
        if (!"SUCCEEDED".equals(answer.evaluationStatus())) throw new IllegalArgumentException("Evaluate the saved answer before preparing a candidate");
        List<InterviewSourceReference> refs = new ArrayList<>();
        refs.add(new InterviewSourceReference(id + "|" + qid + "|" + answer.attemptId(), "INTERVIEW_ANSWER", answer.answer(), "answer"));
        return new InterviewResumeCandidate("IC-" + hash(id + qid + answer.attemptId()).substring(0, 16), id, qid, answer.attemptId(),
                fallback(session.sourceLabel(), "面试补充项目材料"), "", answer.answer(), "", "", "",
                List.of("核对个人职责", "补充使用方法", "补充验证过程", "核对实际结果和数据"), refs, true);
    }

    /** Every API view applies the same mock-mode disclosure policy. */
    public InterviewSession view(InterviewSession session) {
        validateKnowledgeSources(session);
        boolean hidden = liveMock(session);
        List<InterviewSessionQuestion> questions = session.questions().stream()
                .filter(q -> !hidden || !q.followUp())
                .map(q -> hidden ? new InterviewSessionQuestion(q.questionId(), q.order(), q.mainQuestionId(), q.category(),
                        q.difficulty(), q.question(), List.of(), q.followUp(), q.generationSource(), q.sourceReferences(), q.rubricVersion()) : q).toList();
        List<InterviewSessionAnswer> answers = hidden ? session.answers().stream().map(a -> new InterviewSessionAnswer(
                a.questionId(), a.answer(), a.answeredAt(), "PENDING", null, null)).toList() : session.answers();
        List<InterviewAnswerAttempt> attempts = hidden ? session.attempts().stream().map(a -> new InterviewAnswerAttempt(
                a.attemptId(), a.questionId(), a.attemptNo(), a.answer(), a.submittedAt(), "PENDING", null, null,
                a.inputFingerprint(), a.selectedForReport(), a.selectedAt(), a.selectionReason())).toList() : session.attempts();
        return copyAt(session, questions, answers, attempts, hidden ? null : session.report(), hidden ? null : session.partialReport(),
                elapsed(session.timer(), Instant.now()), hidden ? List.of() : session.actionPreviews(), session.status(), session.updatedAt(), !hidden);
    }

    public static boolean liveMock(InterviewSession s) {
        return "MOCK".equals(s.mode()) && "IN_PROGRESS".equals(s.status()) && s.report() == null && s.partialReport() == null;
    }

    private InterviewSession update(String id, String student, UnaryOperator<InterviewSession> mutation) {
        for (int i = 0; i < 3; i++) {
            InterviewSession current = get(id, student);
            InterviewSession next = mutation.apply(current);
            if (next == current || next.equals(current)) return current;
            if (store.replace(current, next)) return next;
        }
        throw new IllegalStateException("Interview changed; reload and retry without discarding your answer");
    }

    private InterviewSession copy(InterviewSession s, List<InterviewSessionQuestion> questions, List<InterviewSessionAnswer> answers,
            List<InterviewAnswerAttempt> attempts, InterviewSessionReport report, InterviewSessionReport partial, InterviewTimer timer,
            List<InterviewActionPreview> previews, String status) {
        return copyAt(s, questions, answers, attempts, report, partial, timer, previews, status, Instant.now(), true);
    }

    private InterviewSession copyAt(InterviewSession s, List<InterviewSessionQuestion> questions, List<InterviewSessionAnswer> answers,
            List<InterviewAnswerAttempt> attempts, InterviewSessionReport report, InterviewSessionReport partial, InterviewTimer timer,
            List<InterviewActionPreview> previews, String status, Instant updated, boolean discloseHistory) {
        List<InterviewSessionReport> partialReports = new ArrayList<>(s.partialReports());
        if (partial != null && !partialReports.contains(partial)) partialReports.add(partial);
        return new InterviewSession(s.sessionId(), s.studentId(), s.resumeId(), s.jobId(), s.matchId(), s.targetRole(), s.contextSnapshot(),
                status, List.copyOf(questions), List.copyOf(answers), report, s.mocked() || (report != null && report.mocked()),
                s.createdAt(), updated, "COMPLETED".equals(status) ? (s.completedAt() == null ? updated : s.completedAt()) : s.completedAt(),
                s.mode(), s.sourceType(), s.sourceId(), s.sourceLabel(), s.sourceMaterial(), s.sourceRequirements(), s.sourceGaps(),
                s.sourceReferences(), List.copyOf(attempts), timer, partial, List.copyOf(previews),
                s.feedbackViewedAfterPartial() || ("MOCK".equals(s.mode()) && partial != null), s.analysisMetadata(),
                discloseHistory ? List.copyOf(partialReports) : List.of());
    }

    private InterviewEvaluationResponse evaluationResponse(InterviewSession session, InterviewAnswerAttempt answer) {
        validateKnowledgeSources(session);
        InterviewSessionQuestion followUp = session.questions().stream().filter(q -> q.followUp() && q.mainQuestionId().equals(answer.questionId())).findFirst().orElse(null);
        return new InterviewEvaluationResponse(session.sessionId(), answer.questionId(), answer.evaluationStatus(), answer.evaluation(), answer.evaluationError(), followUp);
    }

    private static InterviewSessionQuestion question(InterviewSession s, String qid) {
        return s.questions().stream().filter(q -> q.questionId().equals(qid)).findFirst().orElseThrow(() -> new IllegalArgumentException("Interview question not found"));
    }
    private static InterviewAnswerAttempt attempt(InterviewSession s, String qid, String aid) {
        question(s, qid);
        return s.attempts().stream().filter(a -> qid.equals(a.questionId()) && (text(aid) == null ? a.selectedForReport() : aid.equals(a.attemptId())))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Saved answer attempt was not found"));
    }
    private static List<InterviewSessionAnswer> selectedAnswers(List<InterviewAnswerAttempt> attempts) {
        return attempts.stream().filter(InterviewAnswerAttempt::selectedForReport).map(a -> new InterviewSessionAnswer(
                a.questionId(), a.answer(), a.submittedAt(), a.evaluationStatus(), a.evaluation(), a.evaluationError())).toList();
    }
    private static List<String> unanswered(InterviewSession s) {
        Set<String> answered = new HashSet<>(); s.answers().forEach(a -> answered.add(a.questionId()));
        return s.questions().stream().filter(q -> !answered.contains(q.questionId()))
                .filter(q -> !"MOCK".equals(s.mode()) || !q.followUp()).map(InterviewSessionQuestion::questionId).toList();
    }
    private static InterviewTimer elapsed(InterviewTimer timer, Instant now) {
        if (timer == null) return null;
        long seconds = timer.accumulatedSeconds() + (timer.runningSince() == null ? 0 : Math.max(0, Duration.between(timer.runningSince(), now).toSeconds()));
        return new InterviewTimer(timer.startedAt(), timer.pausedAt(), seconds, timer.timerMinutes(),
                timer.timeoutReached() || (timer.timerMinutes() != null && seconds >= timer.timerMinutes() * 60L),
                timer.pausedSeconds(), timer.runningSince() == null ? null : now);
    }
    private static boolean usableEvidence(LearningEvidence e) {
        return e.confirmed() && e.evaluation() != null && !e.evaluation().mocked() && e.evaluation().score() >= 70
                && Set.of("CONFIRMED", "RESUME_CANDIDATE", "SUCCEEDED").contains(e.status());
    }
    private static void requireActive(InterviewSession s) {
        if (!"IN_PROGRESS".equals(s.status())) throw new IllegalArgumentException("Interview session is already finished");
    }
    private static int index(String value, int size) {
        try { int i = Integer.parseInt(value); if (i >= 0 && i < size) return i; } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Source position is invalid");
    }
    private static String choice(String value, String fallback, Set<String> choices, String field) {
        String normalized = text(value) == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!choices.contains(normalized)) throw new IllegalArgumentException("Invalid " + field);
        return normalized;
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private Optional<InterviewSession> accessibleSession(String id, String student) {
        try { return Optional.of(get(id, student)); }
        catch (KnowledgeSourceUnavailableException ex) { return Optional.empty(); }
    }

    private void validateKnowledgeSources(InterviewSession session) {
        String id = session.sourceId();
        if (id != null && id.startsWith("EVIDENCE:")) {
            String[] parts = id.split(":", 4);
            try {
                if (parts.length != 4) throw new IllegalArgumentException();
                career.learningTaskSourceReferences(parts[1], parts[2], session.studentId());
            } catch (IllegalArgumentException ex) { throw new KnowledgeSourceUnavailableException(); }
        }
        if (id != null && id.startsWith("INTERVIEW:")) {
            int separator = id.lastIndexOf(':');
            if (separator <= 10) throw new KnowledgeSourceUnavailableException();
            get(id.substring(10, separator), session.studentId());
        }
        List<InterviewSourceReference> refs = new ArrayList<>(session.sourceReferences());
        session.questions().forEach(question -> refs.addAll(list(question.sourceReferences())));
        validateKnowledgeSources(session.sourceId(), session.sourceMaterial(), refs);
    }

    private void validateKnowledgeSources(String sourceId, String material, List<InterviewSourceReference> references) {
        for (InterviewSourceReference reference : list(references)) if ("KNOWLEDGE_DOCUMENT".equals(reference.kind())) {
            try { career.validateKnowledgeDocumentReference(reference); }
            catch (IllegalArgumentException ex) { throw new KnowledgeSourceUnavailableException(); }
        }
        List<InterviewSourceReference> knowledgeRefs = list(references).stream()
                .filter(ref -> "KNOWLEDGE".equals(ref.kind())).distinct().toList();
        boolean primaryKnowledge = sourceId != null && sourceId.startsWith("KNOWLEDGE:");
        if (!primaryKnowledge && knowledgeRefs.isEmpty()) return;
        Function<String, KnowledgeTopic> validator = knowledgeSourceValidator;
        if (validator == null || knowledgeRefs.isEmpty()) throw new KnowledgeSourceUnavailableException();
        Map<String, KnowledgeTopic> currentTopics = new HashMap<>();
        for (InterviewSourceReference ref : knowledgeRefs) {
            if (ref.sourceId() == null || !ref.sourceId().startsWith("KNOWLEDGE:") || ref.sourceId().length() == 10)
                throw new KnowledgeSourceUnavailableException();
            KnowledgeTopic topic;
            try { topic = currentTopics.computeIfAbsent(ref.sourceId(), key -> validator.apply(key.substring(10))); }
            catch (IllegalArgumentException ex) { throw new KnowledgeSourceUnavailableException(); }
            if (topic == null) throw new KnowledgeSourceUnavailableException();
            String currentMaterial = Objects.toString(topic.content(), "") + "\n" + Objects.toString(topic.example(), "")
                    + "\n" + Objects.toString(topic.practicePrompt(), "");
            if (!"topic/content".equals(ref.location()) && !("topic/content/v" + topic.version()).equals(ref.location())
                    || !Objects.toString(ref.quote(), "").trim().equals(currentMaterial.trim())
                    || primaryKnowledge && ref.sourceId().equals(sourceId)
                            && !Objects.toString(material, "").trim().equals(currentMaterial.trim()))
                throw new KnowledgeSourceUnavailableException();
        }
        if (primaryKnowledge && knowledgeRefs.stream().noneMatch(ref -> sourceId.equals(ref.sourceId())))
            throw new KnowledgeSourceUnavailableException();
    }

    private static boolean knowledgeReference(InterviewSourceReference reference) {
        return "KNOWLEDGE".equals(reference.kind()) || "KNOWLEDGE_DOCUMENT".equals(reference.kind());
    }

    private static final class KnowledgeSourceUnavailableException extends IllegalArgumentException {
        private KnowledgeSourceUnavailableException() {
            super("Knowledge source is unavailable or updated; choose current material and start a new practice");
        }
    }
    private static String reportFingerprint(InterviewSessionReport report) {
        if (report == null) throw new IllegalArgumentException("Generate a report before choosing next actions");
        try { return hash(new ObjectMapper().findAndRegisterModules().writeValueAsString(report)); }
        catch (Exception ex) { throw new IllegalStateException("Unable to prepare interview report snapshot", ex); }
    }
    private static String scopedGenerationContext(String type, String sourceId, Source source) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("sourceType", type, "sourceId", sourceId,
                    "sourceFocus", source.label(), "sourceMaterial", source.material(), "sourceReferences", source.references(),
                    "instruction", "只围绕选定来源出题。能力缺口只询问该项的方法、取舍和验证；项目只使用所选项目已提供的材料，不把其他技能、技术、职责或数据假定为学生经历。"));
        } catch (Exception ex) { throw new IllegalStateException("Unable to prepare interview source context", ex); }
    }
    private static void validateId(String value, String field) {
        if (text(value) != null && (value.length() > 300 || !value.matches("[-A-Za-z0-9._:]+")))
            throw new IllegalArgumentException("Invalid " + field);
    }
    private static String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String fallback(String value, String fallback) { return text(value) == null ? fallback : value; }
    private static String required(String value, String message) { if (text(value) == null) throw new IllegalArgumentException(message); return value.trim(); }
    private static String shortText(String value) { return value.length() <= 60 ? value : value.substring(0, 60); }
    private static String join(String... values) { return Arrays.stream(values).filter(v -> text(v) != null).reduce((a,b) -> a + "\n" + b).orElse(""); }
    private static <T> List<T> list(List<T> values) { return values == null ? List.of() : values; }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private record Source(String label, String material, List<InterviewSourceReference> references, List<String> gaps, String targetRole) {}
}
