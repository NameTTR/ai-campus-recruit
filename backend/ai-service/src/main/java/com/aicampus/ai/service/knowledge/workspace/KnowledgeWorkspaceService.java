package com.aicampus.ai.service.knowledge.workspace;

import static com.aicampus.common.dto.KnowledgeWorkspaceModels.*;

import com.aicampus.common.dto.KnowledgeAnswerResponse;
import com.aicampus.common.dto.InterviewSession;
import com.aicampus.common.dto.LearningPlan;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.evidence.SkillOntology;
import com.aicampus.ai.service.core.AiCareerCoreService;
import com.aicampus.ai.service.core.RecruitmentContextClient;
import com.aicampus.ai.service.KnowledgeBaseService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Account-owned workflow around the searchable knowledge catalog. */
@Service
public class KnowledgeWorkspaceService {
    private static final String ITEM = "ITEM";
    private static final String HISTORY = "HISTORY";
    private static final String PRACTICE = "PRACTICE";
    private static final String ATTEMPT = "ATTEMPT";
    private static final String ACTION = "ACTION";
    private final KnowledgeWorkspaceStore store;
    private final KnowledgeCatalogService catalog;
    private final KnowledgePracticeEvaluator evaluator;
    private KnowledgeActionBridge actionBridge;
    private RecruitmentContextClient contextClient;
    private AiCareerCoreService career;
    private KnowledgeBaseService knowledge;
    private final Object[] locks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public KnowledgeWorkspaceService(KnowledgeWorkspaceStore store, KnowledgeCatalogService catalog,
            KnowledgePracticeEvaluator evaluator) {
        this.store = store;
        this.catalog = catalog;
        this.evaluator = evaluator;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setActionBridge(KnowledgeActionBridge actionBridge) { this.actionBridge = actionBridge; }

    @org.springframework.beans.factory.annotation.Autowired
    public void setContextClient(RecruitmentContextClient contextClient) { this.contextClient = contextClient; }

    @org.springframework.beans.factory.annotation.Autowired
    public void setCareerService(AiCareerCoreService career) { this.career = career; }

    @org.springframework.beans.factory.annotation.Autowired
    public void setKnowledgeService(KnowledgeBaseService knowledge) { this.knowledge = knowledge; }

    public KnowledgeAnswerResponse query(KnowledgeQuery request, String studentId, String role) {
        requireStudent(studentId, role);
        validateQuery(request, studentId, role);
        KnowledgeAnswerResponse response = catalog.search(request, studentId, role);
        String id = fingerprint(studentId + request + response.inputFingerprint() + response.permissionVersion());
        store.insert(HISTORY, id, studentId, new KnowledgeWorkspaceHistory(id, studentId,
                request == null ? null : request.query(), request == null ? null : request.roleDirection(),
                request == null ? null : request.jobId(), request == null ? null : request.matchId(), response,
                response == null ? null : response.permissionVersion(), Instant.now()));
        return response;
    }

    public List<KnowledgeWorkspaceHistory> history(String studentId, String role, int limit) {
        requireStudent(studentId, role);
        return store.list(HISTORY, studentId, KnowledgeWorkspaceHistory.class).stream()
                .sorted(Comparator.comparing(KnowledgeWorkspaceHistory::createdAt).reversed())
                .limit(Math.max(1, Math.min(limit <= 0 ? 30 : limit, 100)))
                .map(value -> safeHistory(value, role)).toList();
    }

    public boolean deleteHistory(String id, String studentId, String role) {
        requireStudent(studentId, role);
        if (store.get(HISTORY, id, studentId, KnowledgeWorkspaceHistory.class).isEmpty()) throw new IllegalArgumentException("History not found");
        return store.delete(HISTORY, id, studentId);
    }

    public List<KnowledgeWorkspaceRecommendation> recommendations(String roleDirection, String skill,
            String difficulty, String contentType, String studentId, String role) {
        return recommendations(new KnowledgeQuery(null, roleDirection, skill, difficulty, contentType,
                false, null, null, null, null, null, null), studentId, role);
    }

    public List<KnowledgeWorkspaceRecommendation> recommendations(KnowledgeQuery request, String studentId, String role) {
        requireStudent(studentId, role);
        QueryContext context = validateQuery(request, studentId, role);
        String direction = request == null ? null : request.roleDirection();
        if (!hasText(direction) && request != null) direction = request.targetRole();
        if (!hasText(direction) && context.recruitment() != null && context.recruitment().job() != null)
            direction = context.recruitment().job().title();
        if (!hasText(direction) && context.plan() != null) direction = context.plan().targetRole();
        if (!hasText(direction) && context.interview() != null) direction = context.interview().targetRole();
        List<KnowledgeTopic> topics = catalog.listTopics(direction, request == null ? null : request.skill(),
                request == null ? null : request.difficulty(), request == null ? null : request.contentType(), role);
        LinkedHashMap<String, RecommendationReason> reasons = new LinkedHashMap<>();
        if (context.interview() != null && context.interview().report() != null) {
            for (String gap : safe(context.interview().report().gaps())) addReason(topics, reasons, gap,
                    "练习中出现不足：" + gap, "PRACTICE_GAP");
        }
        Map<String, KnowledgePracticeAttempt> latest = new LinkedHashMap<>();
        Comparator<KnowledgePracticeAttempt> attemptOrder = Comparator.comparing(KnowledgePracticeAttempt::createdAt)
                .thenComparingInt(KnowledgePracticeAttempt::attemptNo);
        for (KnowledgePracticeAttempt attempt : store.list(ATTEMPT, studentId, KnowledgePracticeAttempt.class)) {
            String key = attempt.practiceId() + "\n" + attempt.questionId();
            latest.merge(key, attempt, (prior, candidate) -> attemptOrder.compare(candidate, prior) > 0 ? candidate : prior);
        }
        for (KnowledgePracticeAttempt attempt : latest.values()) {
            if (attempt.evaluationSnapshot() == null || !"SUCCEEDED".equals(attempt.status())) continue;
            if (attempt.evaluationSnapshot().score() >= 60 && "SUPPORTED".equals(attempt.evaluationSnapshot().judgement())) continue;
            store.get(PRACTICE, attempt.practiceId(), studentId, KnowledgePractice.class).ifPresent(practice -> {
                try { validatePracticeSource(practice, studentId, role); }
                catch (RuntimeException ex) { return; }
                topics.stream().filter(topic -> topic.id().equals(practice.topicId())).forEach(topic ->
                    reasons.putIfAbsent(topic.id(), new RecommendationReason("短练习仍需补充：" + attempt.evaluationSnapshot().nextAction(), "PRACTICE_GAP")));
            });
        }
        MatchResult match = context.recruitment() == null ? null : context.recruitment().match();
        JobSummary job = context.recruitment() == null ? null : context.recruitment().job();
        if (job == null && match != null && match.details() != null) job = match.details().jobSnapshot();
        Map<String, String> missing = new LinkedHashMap<>();
        if (match != null && match.details() != null) match.details().requirements().stream().filter(requirement -> !requirement.supported())
                .forEach(requirement -> missing.put(SkillOntology.normalize(requirement.skill()), requirement.skill()));
        else if (match != null) safe(match.missingSkills()).forEach(gap -> missing.put(SkillOntology.normalize(gap), gap));
        if (job != null) {
            final JobSummary selectedJob = job;
            List<String> requirements = List.copyOf(SkillOntology.index(safe(job.requiredSkills())).values());
            List<String> orderedRequirements = requirements.stream().sorted(Comparator.comparingInt(requirement ->
                    "REQUIRED".equals(requirementTier(selectedJob, requirement)) ? 0 : 1)).toList();
            for (String requirement : orderedRequirements) {
                String tier = requirementTier(job, requirement);
                String quote = requirementQuote(job, requirement);
                boolean absent = missing.containsKey(SkillOntology.normalize(requirement));
                String reason = ("REQUIRED".equals(tier) ? "岗位明确要求：" : "PREFERRED".equals(tier) ? "岗位优先条件：" : "岗位要求：")
                        + (hasText(quote) ? quote : requirement);
                if (absent) reason += "；材料中尚未体现，可补充已有经历的表达或安排练习";
                addReason(topics, reasons, requirement, reason, absent ? "MATERIAL_NOT_SHOWN" : "JOB_REQUIREMENT");
            }
        } else if (match != null) {
            for (String requirement : safe(match.requiredSkillsSnapshot())) addReason(topics, reasons, requirement,
                    missing.containsKey(SkillOntology.normalize(requirement)) ? "匹配记录中材料尚未体现“" + requirement + "”，建议先核对已有经历"
                            : "匹配记录中的岗位要求：" + requirement,
                    missing.containsKey(SkillOntology.normalize(requirement)) ? "MATERIAL_NOT_SHOWN" : "JOB_REQUIREMENT");
        }
        for (String absent : missing.values()) addReason(topics, reasons, absent, "材料中尚未体现“" + absent + "”，建议先核对已有经历", "MATERIAL_NOT_SHOWN");
        if (context.plan() != null) context.plan().tasks().stream()
                .filter(task -> !Set.of("DONE", "COMPLETED", "SKIPPED").contains(task.status())
                        && !"SOURCE_UNAVAILABLE".equals(task.referenceStatus())).forEach(task ->
                addReason(topics, reasons, task.skillGap(), "学习任务需要：" + task.title(), "LEARNING_TASK"));
        if (request != null && hasText(request.skill())) addReason(topics, reasons, request.skill(),
                "你希望补充学习“" + request.skill() + "”", "STUDENT_REQUEST");
        if (reasons.isEmpty()) topics.forEach(topic -> reasons.put(topic.id(),
                new RecommendationReason("通用岗位建议：先理解基础知识，再通过练习验证", "GENERAL")));
        LinkedHashMap<String, RecommendationReason> ordered = new LinkedHashMap<>();
        for (String id : List.copyOf(reasons.keySet())) orderPrerequisites(id, topics, reasons, ordered, new java.util.HashSet<>());
        List<KnowledgeWorkspaceRecommendation> result = new ArrayList<>();
        int priority = 1;
        for (Map.Entry<String, RecommendationReason> entry : ordered.entrySet()) {
            KnowledgeTopic topic = topics.stream().filter(value -> entry.getKey().equals(value.id())).findFirst().orElseThrow();
            result.add(new KnowledgeWorkspaceRecommendation("REC-" + topic.id(), topic.id(), topic.title(),
                    topic.role(), topic.skill(), entry.getValue().reason(), entry.getValue().gapType(), priority++, topic.estimatedMinutes(),
                    topic.prerequisites() == null ? List.of() : topic.prerequisites()));
            if (result.size() == 3) break;
        }
        return result;
    }

    public List<KnowledgeWorkspaceItem> items(String studentId, String role, String kind, String status) {
        requireStudent(studentId, role);
        return store.list(ITEM, studentId, KnowledgeWorkspaceItem.class).stream()
                .filter(item -> !hasText(kind) || kind.equalsIgnoreCase(item.kind()))
                .sorted(Comparator.comparing(KnowledgeWorkspaceItem::updatedAt).reversed())
                .map(item -> safeItem(item, role))
                .filter(item -> !hasText(status) || status.equalsIgnoreCase(item.status())).toList();
    }

    public KnowledgeWorkspaceItem saveItem(KnowledgeItemRequest request, String studentId, String role) {
        requireStudent(studentId, role);
        if (request == null || !hasText(request.topicId())) throw new IllegalArgumentException("topicId is required");
        synchronized (lock(studentId + request.topicId())) { return saveItemLocked(request, studentId, role); }
    }

    private KnowledgeWorkspaceItem saveItemLocked(KnowledgeItemRequest request, String studentId, String role) {
        KnowledgeTopic topic = catalog.topic(request.topicId(), role);
        if (topic == null) throw new IllegalArgumentException("Topic is not available");
        String kind = valueOr(request.kind(), "BOOKMARK").toUpperCase(Locale.ROOT);
        if ("LEARNING".equals(kind)) kind = "STUDY";
        if (!Set.of("BOOKMARK", "NOTE", "STUDY").contains(kind)) throw new IllegalArgumentException("Invalid item kind");
        String requestedStatus = hasText(request.status()) ? request.status().toUpperCase(Locale.ROOT) : null;
        if ("SELF_REPORTED_MASTERED".equals(requestedStatus)) requestedStatus = "SELF_MASTERED";
        if (requestedStatus != null && !Set.of("TO_LEARN", "LEARNING", "SELF_MASTERED", "TO_REVIEW").contains(requestedStatus))
            throw new IllegalArgumentException("Invalid learning status");
        final String itemKind = kind;
        if (request.note() != null && request.note().length() > 10000) throw new IllegalArgumentException("笔记不能超过 10000 字");
        Instant now = Instant.now();
        KnowledgeWorkspaceItem old = store.list(ITEM, studentId, KnowledgeWorkspaceItem.class).stream()
                .filter(item -> item.topicId().equals(topic.id()) && item.kind().equalsIgnoreCase(itemKind)).findFirst().orElse(null);
        if (old != null) {
            if (request.expectedRevision() == null && (request.note() == null || Objects.equals(old.note(), request.note()))
                    && (request.status() == null || Objects.equals(old.status(), requestedStatus))
                    && request.intervalDays() == null && request.reviewEnabled() == null) return old;
            if (request.expectedRevision() == null || request.expectedRevision() != old.revision())
                throw new IllegalStateException("学习记录已被修改，请刷新后重试");
            long revision = old.revision() + 1;
            Instant next = nextReview(request, old, now);
            KnowledgeWorkspaceItem updated = new KnowledgeWorkspaceItem(old.itemId(), studentId, topic.id(), kind,
                    valueOr(requestedStatus, old.status()), request.note() == null ? old.note() : request.note(),
                    old.scheduledAt(), old.lastReviewedAt(), next, intervals(request, old), revision, old.createdAt(), now);
            if (!store.replace(ITEM, old.itemId(), studentId, old, updated)) throw new IllegalStateException("学习记录已被修改，请刷新后重试");
            updateReviewSettings(request, updated, old);
            return updated;
        }
        if (request.expectedRevision() != null && request.expectedRevision() != 0)
            throw new IllegalStateException("学习记录不存在，请刷新后重试");
        Instant next = nextReview(request, null, now);
        KnowledgeWorkspaceItem created = new KnowledgeWorkspaceItem("KWI-" + UUID.randomUUID(), studentId, topic.id(), kind,
                valueOr(requestedStatus, "TO_LEARN"), request.note(), now, null, next, intervals(request, null),
                1, now, now);
        store.put(ITEM, created.itemId(), studentId, created);
        updateReviewSettings(request, created, null);
        return created;
    }

    public boolean deleteItem(String id, String studentId, String role) {
        requireStudent(studentId, role);
        if (store.get(ITEM, id, studentId, KnowledgeWorkspaceItem.class).isEmpty()) throw new IllegalArgumentException("Learning item not found");
        store.delete("REVIEW_SCHEDULE", id, studentId);
        return store.delete(ITEM, id, studentId);
    }

    public KnowledgeWorkspaceItem reviewItem(String id, KnowledgeReviewRequest request, String studentId, String role) {
        requireStudent(studentId, role);
        if (request == null) throw new IllegalArgumentException("Review result is required");
        synchronized (lock(studentId + id)) { return reviewItemLocked(id, request, studentId, role); }
    }

    private KnowledgeWorkspaceItem reviewItemLocked(String id, KnowledgeReviewRequest request, String studentId, String role) {
        KnowledgeWorkspaceItem item = store.get(ITEM, id, studentId, KnowledgeWorkspaceItem.class)
                .orElseThrow(() -> new IllegalArgumentException("Learning item not found"));
        catalog.topic(item.topicId(), role);
        ReviewSchedule schedule = store.get("REVIEW_SCHEDULE", id, studentId, ReviewSchedule.class).orElse(new ReviewSchedule(0, true));
        if (!schedule.enabled()) throw new IllegalArgumentException("复习安排已关闭");
        Instant now = Instant.now();
        if (item.lastReviewedAt() != null && item.lastReviewedAt().atZone(ZONE).toLocalDate().equals(now.atZone(ZONE).toLocalDate()))
            return safeItem(item, role);
        List<Integer> intervals = item.intervalDays() == null || item.intervalDays().isEmpty()
                ? List.of(1, 3, 7, 14) : item.intervalDays();
        int nextIndex = request.passed() ? Math.min(intervals.size() - 1, schedule.index() + 1) : 0;
        int days = intervals.get(Math.max(0, Math.min(nextIndex, intervals.size() - 1)));
        String status = request.passed() ? "SELF_MASTERED" : "LEARNING";
        KnowledgeWorkspaceItem updated = new KnowledgeWorkspaceItem(item.itemId(), item.studentId(), item.topicId(), item.kind(),
                status, item.note(), item.scheduledAt(), now, nextDate(now, request.passed() ? days : 1), intervals,
                item.revision() + 1, item.createdAt(), now);
        if (!store.replace(ITEM, id, studentId, item, updated)) throw new IllegalStateException("复习记录已变化，请刷新后重试");
        store.put("REVIEW_SCHEDULE", id, studentId, new ReviewSchedule(nextIndex, true));
        return updated;
    }

    public KnowledgePractice createPractice(String topicId, String studentId, String role) {
        requireStudent(studentId, role);
        KnowledgeTopic topic = catalog.topic(topicId, role);
        if (topic == null) throw new IllegalArgumentException("Topic is not available");
        KnowledgeLibraryDocument library = catalog.library(topic.documentId(), role);
        List<KnowledgeSourceLocation> locations = library == null || library.locations() == null ? List.of() : library.locations();
        List<String> refs = locations.stream().limit(5).map(KnowledgeSourceLocation::chunkId).filter(Objects::nonNull).toList();
        if (refs.isEmpty()) throw new IllegalArgumentException("Topic has no readable source");
        String practiceId = "KPR-" + fingerprint(studentId + topic.id() + library.version());
        synchronized (lock(practiceId)) {
        KnowledgePractice previous = store.get(PRACTICE, practiceId, studentId, KnowledgePractice.class).orElse(null);
        if (previous != null) return practice(practiceId, studentId, role);
        String base = valueOr(topic.practicePrompt(), "请用自己的话解释这个概念，并说明一个应用场景和验证方法。");
        List<String> understandingRubric = List.of(valueOr(topic.summary(), topic.content()), "使用自己的话解释，并说明适用条件");
        List<String> exampleRubric = List.of(valueOr(topic.example(), topic.content()), "区分方案的适用范围与限制，不能把资料没有的结论当事实");
        List<String> applicationRubric = List.of(base, "说明自己的操作步骤和可以核对的验证方法，不虚构结果");
        List<KnowledgePracticeQuestion> questions = List.of(
                new KnowledgePracticeQuestion(practiceId + "-1", 1, "UNDERSTANDING", "请用自己的话解释：" + topic.title() + "。说明它解决什么问题。", refs, understandingRubric),
                new KnowledgePracticeQuestion(practiceId + "-2", 2, "UNDERSTANDING", "结合资料中的场景，解释主要选择理由和一个适用限制。\n"
                        + valueOr(topic.example(), "请从原文中选择一个适用场景，不要补造案例。"), refs, exampleRubric),
                new KnowledgePracticeQuestion(practiceId + "-3", 3, "APPLICATION", "针对下面的实践，写出前三个操作步骤以及如何验证，而不要求在此完成整个作品。\n" + base, refs, applicationRubric));
        KnowledgePractice practice = new KnowledgePractice(practiceId, topic.id(), topic.title() + "短练习", questions,
                10, locations, Instant.now());
        store.put("PRACTICE_SOURCE", practiceId, studentId, new PracticeSource(topic.documentId(), library.version()));
        store.put(PRACTICE, practiceId, studentId, practice);
        return practice;
        }
    }

    public KnowledgePractice practice(String id, String studentId, String role) {
        requireStudent(studentId, role);
        KnowledgePractice practice = store.get(PRACTICE, id, studentId, KnowledgePractice.class)
                .orElseThrow(() -> new IllegalArgumentException("Practice not found"));
        validatePracticeSource(practice, studentId, role);
        return practice;
    }

    public List<KnowledgePracticeAttempt> attempts(String practiceId, String studentId, String role) {
        practice(practiceId, studentId, role);
        return store.list(ATTEMPT, studentId, KnowledgePracticeAttempt.class).stream()
                .filter(attempt -> practiceId.equals(attempt.practiceId()))
                .sorted(Comparator.comparing(KnowledgePracticeAttempt::createdAt)).toList();
    }

    public KnowledgePracticeAttempt answer(String practiceId, KnowledgePracticeAnswerRequest request,
            String studentId, String role) {
        KnowledgePractice practice = practice(practiceId, studentId, role);
        if (request == null || !hasText(request.questionId()) || !hasText(request.answer())) throw new IllegalArgumentException("questionId and answer are required");
        if (request.answer().length() > 20000) throw new IllegalArgumentException("回答不能超过 20000 字");
        synchronized (lock(studentId + practiceId + request.questionId())) { return answerLocked(practice, request, studentId, role); }
    }

    private KnowledgePracticeAttempt answerLocked(KnowledgePractice practice, KnowledgePracticeAnswerRequest request,
            String studentId, String role) {
        String practiceId = practice.practiceId();
        KnowledgePracticeQuestion question = practice.questions().stream().filter(q -> q.questionId().equals(request.questionId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        String fp = fingerprint(studentId + "\n" + practiceId + "\n" + question.questionId() + "\n" + request.answer().trim()
                + "\n" + KnowledgePracticeEvaluator.VERSION + "\n" + evaluator.modelVersion());
        KnowledgePracticeAttempt duplicate = attempts(practiceId, studentId, role).stream().filter(a -> fp.equals(a.inputFingerprint())).findFirst().orElse(null);
        if (duplicate != null) return duplicate;
        int number = (int) attempts(practiceId, studentId, role).stream().filter(a -> a.questionId().equals(question.questionId())).count() + 1;
        Instant now = Instant.now();
        KnowledgePracticeAttempt saved = new KnowledgePracticeAttempt("KPA-" + fp, studentId, practiceId,
                question.questionId(), number, request.answer(), "RECORDED", null, fp, number == 1, now, null);
        if (!store.insert(ATTEMPT, saved.attemptId(), studentId, saved)) return store.get(ATTEMPT, saved.attemptId(), studentId, KnowledgePracticeAttempt.class).orElseThrow();
        store.put("ATTEMPT_VERSION", saved.attemptId(), studentId, new AttemptVersion(KnowledgePracticeEvaluator.VERSION, evaluator.modelVersion()));
        return saved;
    }

    public KnowledgePracticeAttempt retry(String practiceId, String attemptId, String studentId, String role) {
        KnowledgePractice practice = practice(practiceId, studentId, role);
        KnowledgePracticeAttempt old = store.get(ATTEMPT, attemptId, studentId, KnowledgePracticeAttempt.class)
                .filter(a -> practiceId.equals(a.practiceId())).orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        synchronized (lock(studentId + practiceId + old.questionId())) {
            old = store.get(ATTEMPT, attemptId, studentId, KnowledgePracticeAttempt.class).orElseThrow();
            if (Set.of("SUCCEEDED", "DEMO").contains(old.status())) return old;
            Instant claimedAt = old.evaluatedAt() == null ? old.createdAt() : old.evaluatedAt();
            if ("EVALUATING".equals(old.status()) && claimedAt.isAfter(Instant.now().minus(5, ChronoUnit.MINUTES))) return old;
            if (!Set.of("RECORDED", "FAILED", "EVALUATING").contains(old.status())) throw new IllegalArgumentException("评价状态不允许重试");
            KnowledgePracticeAttempt claimed = new KnowledgePracticeAttempt(old.attemptId(), old.studentId(), old.practiceId(), old.questionId(), old.attemptNo(), old.answer(), "EVALUATING", null,
                    old.inputFingerprint(), old.selected(), old.createdAt(), Instant.now());
            if (!store.replace(ATTEMPT, attemptId, studentId, old, claimed)) return store.get(ATTEMPT, attemptId, studentId, KnowledgePracticeAttempt.class).orElseThrow();
            final String questionId = old.questionId();
            KnowledgePracticeQuestion question = practice.questions().stream().filter(q -> q.questionId().equals(questionId)).findFirst().orElseThrow();
            return evaluateAttempt(claimed, practice, question, role);
        }
    }

    private KnowledgePracticeAttempt evaluateAttempt(KnowledgePracticeAttempt old, KnowledgePractice practice,
            KnowledgePracticeQuestion question, String role) {
        try {
            validatePracticeSource(practice, old.studentId(), role);
            KnowledgePracticeEvaluation evaluation = evaluator.evaluate(question, old.answer(), practice.sourceLocations());
            validatePracticeSource(practice, old.studentId(), role);
            KnowledgePracticeAttempt updated = new KnowledgePracticeAttempt(old.attemptId(), old.studentId(), old.practiceId(), old.questionId(), old.attemptNo(), old.answer(), evaluation.status(), evaluation,
                    old.inputFingerprint(), old.selected(), old.createdAt(), Instant.now());
            store.put(ATTEMPT, old.attemptId(), old.studentId(), updated);
            return updated;
        } catch (RuntimeException ex) {
            KnowledgePracticeEvaluation failure = new KnowledgePracticeEvaluation("FAILED", 0, "INSUFFICIENT_EVIDENCE", "",
                    "评价未完成，已保留原回答", "请稍后重试；若资料更新或权限变化，请重新创建练习", List.of(), Instant.now());
            KnowledgePracticeAttempt failed = new KnowledgePracticeAttempt(old.attemptId(), old.studentId(), old.practiceId(), old.questionId(), old.attemptNo(), old.answer(),
                    "FAILED", failure, old.inputFingerprint(), old.selected(), old.createdAt(), Instant.now());
            store.put(ATTEMPT, old.attemptId(), old.studentId(), failed);
            return failed;
        }
    }

    public KnowledgeActionPreview previewAction(KnowledgeActionRequest request, String studentId, String role) {
        requireStudent(studentId, role);
        if (request == null) throw new IllegalArgumentException("Action preview request is required");
        if (actionBridge == null) throw new IllegalStateException("Action integration is unavailable");
        KnowledgeTopic topic = catalog.topic(request.topicId(), role);
        if (topic == null) throw new IllegalArgumentException("Topic is not available");
        if (request.practiceId() != null && !request.practiceId().isBlank()) {
            KnowledgePractice selected = practice(request.practiceId(), studentId, role);
            if (!topic.id().equals(selected.topicId())) throw new IllegalArgumentException("Practice does not belong to this topic");
        }
        synchronized (lock(studentId + "ACTION")) {
        Map<String, Object> payload = actionBridge.preview(studentId, role, request, topic);
        String fingerprint = fingerprint(studentId + request.toString() + topic.version()
                + Objects.toString(payload.get("revisionId"), "") + Objects.toString(payload.get("baseUpdatedAt"), ""));
        KnowledgeActionPreview previous = store.get(ACTION, "KAP-" + fingerprint, studentId, KnowledgeActionPreview.class).orElse(null);
        if (previous != null) return publicAction(previous);
        KnowledgeActionPreview preview = new KnowledgeActionPreview("KAP-" + fingerprint, studentId,
                valueOr(request.type(), "LEARNING_PLAN"), topic.id(), "学习“" + topic.title() + "”",
                "把知识练习转成可确认的下一步行动", Math.max(5, topic.estimatedMinutes()),
                Objects.toString(payload.get("impact"), "确认后启用行动，不会直接改变技能证据或简历"), "DRAFT", payload, Instant.now());
        store.put(ACTION, preview.previewId(), studentId, preview);
        return publicAction(preview);
        }
    }

    public KnowledgeActionPreview confirmAction(String id, String studentId, String role) {
        requireStudent(studentId, role);
        synchronized (lock(studentId + "ACTION")) {
        KnowledgeActionPreview preview = store.get(ACTION, id, studentId, KnowledgeActionPreview.class)
                .orElseThrow(() -> new IllegalArgumentException("Action preview not found"));
        KnowledgeTopic current = catalog.topic(preview.topicId(), role);
        if (current == null) throw new IllegalArgumentException("Knowledge topic is not available");
        Object version = preview.payload().get("topicVersion");
        if (version != null && Integer.parseInt(version.toString()) != current.version()) throw new IllegalStateException("资料已更新，请重新预览行动");
        if (!"DRAFT".equalsIgnoreCase(preview.status())) return publicAction(preview);
        if (actionBridge == null) throw new IllegalStateException("Action integration is unavailable");
        Map<String, Object> confirmedPayload = actionBridge.confirm(studentId, role, preview);
        KnowledgeActionPreview confirmed = new KnowledgeActionPreview(preview.previewId(), preview.studentId(), preview.type(), preview.topicId(), preview.title(), preview.reason(), preview.estimatedMinutes(), preview.impact(), "CONFIRMED", confirmedPayload, preview.createdAt());
        store.put(ACTION, id, studentId, confirmed);
        return publicAction(confirmed);
        }
    }

    public KnowledgeWorkspaceHistory historyItem(String id, String studentId, String role) {
        requireStudent(studentId, role);
        return safeHistory(store.get(HISTORY, id, studentId, KnowledgeWorkspaceHistory.class).orElseThrow(() -> new IllegalArgumentException("History not found")), role);
    }

    private static void requireStudent(String studentId, String role) {
        if (!hasText(studentId) || !"STUDENT".equalsIgnoreCase(valueOr(role, ""))) throw new IllegalArgumentException("A student identity is required");
    }
    private static String valueOr(String value, String fallback) { return hasText(value) ? value.trim() : fallback; }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static List<Integer> intervals(KnowledgeItemRequest request, KnowledgeWorkspaceItem old) {
        List<Integer> days = request != null && request.intervalDays() != null && !request.intervalDays().isEmpty()
                ? List.copyOf(request.intervalDays()) : old != null && old.intervalDays() != null && !old.intervalDays().isEmpty()
                    ? old.intervalDays() : List.of(1, 3, 7, 14);
        if (days.size() > 12) throw new IllegalArgumentException("最多设置 12 个复习间隔");
        int last = 0;
        for (Integer day : days) {
            if (day == null || day <= last || day > 365) throw new IllegalArgumentException("复习间隔必须递增且在 1 到 365 天之间");
            last = day;
        }
        return days;
    }
    private static Instant nextReview(KnowledgeItemRequest request, KnowledgeWorkspaceItem old, Instant now) {
        if (Boolean.FALSE.equals(request.reviewEnabled())) return null;
        if (Boolean.TRUE.equals(request.reviewEnabled()) || "SELF_MASTERED".equalsIgnoreCase(request.status())
                || "SELF_REPORTED_MASTERED".equalsIgnoreCase(request.status())) {
            if (old != null && old.nextReviewAt() != null && request.intervalDays() == null
                    && Objects.equals(old.status(), request.status())) return old.nextReviewAt();
            return nextDate(now, intervals(request, old).get(0));
        }
        return old == null ? null : old.nextReviewAt();
    }
    private static Instant nextDate(Instant now, int days) { return now.atZone(ZONE).plusDays(days).toInstant(); }
    private void updateReviewSettings(KnowledgeItemRequest request, KnowledgeWorkspaceItem value, KnowledgeWorkspaceItem old) {
        if (old == null || request.intervalDays() != null || request.reviewEnabled() != null
                || (!Objects.equals(old.status(), value.status()) && "SELF_MASTERED".equals(value.status()))) {
            store.put("REVIEW_SCHEDULE", value.itemId(), value.studentId(), new ReviewSchedule(0, value.nextReviewAt() != null));
        }
    }

    private KnowledgeWorkspaceItem safeItem(KnowledgeWorkspaceItem item, String role) {
        try { catalog.topic(item.topicId(), role); }
        catch (RuntimeException ex) {
            return new KnowledgeWorkspaceItem(item.itemId(), item.studentId(), item.topicId(), item.kind(), "UNAVAILABLE",
                    null, item.scheduledAt(), item.lastReviewedAt(), null, item.intervalDays(), item.revision(), item.createdAt(), item.updatedAt());
        }
        if (item.nextReviewAt() != null && !item.nextReviewAt().isAfter(Instant.now()))
            return new KnowledgeWorkspaceItem(item.itemId(), item.studentId(), item.topicId(), item.kind(), "TO_REVIEW", item.note(),
                    item.scheduledAt(), item.lastReviewedAt(), item.nextReviewAt(), item.intervalDays(), item.revision(), item.createdAt(), item.updatedAt());
        if ("TO_REVIEW".equals(item.status()) && item.nextReviewAt() != null)
            return new KnowledgeWorkspaceItem(item.itemId(), item.studentId(), item.topicId(), item.kind(), "LEARNING", item.note(),
                    item.scheduledAt(), item.lastReviewedAt(), item.nextReviewAt(), item.intervalDays(), item.revision(), item.createdAt(), item.updatedAt());
        return item;
    }

    private KnowledgeWorkspaceHistory safeHistory(KnowledgeWorkspaceHistory value, String role) {
        KnowledgeAnswerResponse answer = value.answerSnapshot();
        boolean invalid = answer == null;
        if (answer != null) {
            invalid = knowledge != null && !Objects.equals(value.permissionVersion(), knowledge.permissionVersion());
            if (!invalid) for (var citation : answer.citations() == null ? List.<com.aicampus.common.dto.KnowledgeCitation>of() : answer.citations()) {
                try {
                    KnowledgeLibraryDocument library = catalog.library(citation.documentId(), role);
                    if (citation.snippet() != null && !library.content().contains(citation.snippet())) invalid = true;
                } catch (RuntimeException ex) { invalid = true; }
            }
        }
        if (invalid) answer = new KnowledgeAnswerResponse(null, "资料已更新或权限已变化，请重新查询。", List.of(), true,
                "history", Instant.now(), "STALE", "RETRIEVAL_ONLY", "PERMISSIONS_CHANGED", "workspace-v1",
                knowledge == null ? null : knowledge.permissionVersion(), List.of(), null);
        return new KnowledgeWorkspaceHistory(value.historyId(), value.studentId(), value.query(), value.role(), value.jobId(),
                value.matchId(), answer, value.permissionVersion(), value.createdAt());
    }

    private void validatePracticeSource(KnowledgePractice practice, String studentId, String role) {
        KnowledgeTopic current = catalog.topic(practice.topicId(), role);
        KnowledgeLibraryDocument library = catalog.library(current.documentId(), role);
        PracticeSource source = store.get("PRACTICE_SOURCE", practice.practiceId(), studentId, PracticeSource.class).orElse(null);
        if (source != null && (!source.documentId().equals(library.documentId()) || source.version() != library.version()))
            throw new IllegalArgumentException("练习引用的资料已更新，请重新创建练习");
        for (KnowledgeSourceLocation saved : practice.sourceLocations()) {
            boolean found = library.locations().stream().anyMatch(location -> Objects.equals(location.chunkId(), saved.chunkId())
                    && Objects.equals(location.snippet(), saved.snippet()));
            if (!found) throw new IllegalArgumentException("练习引用已失效，请重新创建练习");
        }
    }

    private KnowledgeActionPreview publicAction(KnowledgeActionPreview value) {
        Map<String, Object> payload = new LinkedHashMap<>(value.payload() == null ? Map.of() : value.payload());
        payload.remove("sourceMaterial");
        return new KnowledgeActionPreview(value.previewId(), value.studentId(), value.type(), value.topicId(), value.title(),
                value.reason(), value.estimatedMinutes(), value.impact(), value.status(), Map.copyOf(payload), value.createdAt());
    }

    private QueryContext validateQuery(KnowledgeQuery request, String studentId, String role) {
        if (request == null) throw new IllegalArgumentException("Knowledge query is required");
        RecruitmentContextClient.ValidatedContext context = null;
        if (hasText(request.resumeId()) || hasText(request.jobId()) || hasText(request.matchId())) {
            if (contextClient == null) throw new IllegalStateException("Recruitment context validation unavailable");
            context = contextClient.validate(studentId, request.resumeId(), request.jobId(), request.matchId(), role);
        }
        LearningPlan plan = null;
        if (hasText(request.planId())) {
            if (career == null) throw new IllegalStateException("Learning context validation unavailable");
            plan = career.getLearningPlan(request.planId(), studentId);
        }
        InterviewSession interview = null;
        if (hasText(request.interviewSessionId())) {
            if (career == null) throw new IllegalStateException("Interview context validation unavailable");
            interview = career.getInterviewSession(request.interviewSessionId(), studentId);
            if (interview != null && "MOCK".equals(interview.mode()) && interview.report() == null)
                interview = null;
        }
        if (plan != null && request.jobId() != null && plan.jobId() != null && !request.jobId().equals(plan.jobId()))
            throw new IllegalArgumentException("Learning plan does not match the selected job");
        if (interview != null && request.jobId() != null && interview.jobId() != null && !request.jobId().equals(interview.jobId()))
            throw new IllegalArgumentException("Interview does not match the selected job");
        return new QueryContext(context, plan, interview);
    }

    private static void addReason(List<KnowledgeTopic> topics, Map<String, RecommendationReason> reasons,
            String skillOrGap, String reason, String type) {
        if (!hasText(skillOrGap)) return;
        for (KnowledgeTopic topic : topics) {
            if (SkillOntology.same(skillOrGap, topic.skill()) || SkillOntology.mentions(skillOrGap, topic.skill())
                    || topic.skill().toLowerCase(Locale.ROOT).contains(skillOrGap.toLowerCase(Locale.ROOT)))
                reasons.putIfAbsent(topic.id(), new RecommendationReason(reason, type));
        }
    }

    private static String requirementQuote(JobSummary job, String skill) {
        return java.util.Arrays.stream(Objects.toString(job.description(), "").split("[。！？；;\\r\\n：:]+"))
                .map(String::trim).filter(clause -> SkillOntology.mentions(clause, skill)).findFirst().orElse("");
    }
    private static String requirementTier(JobSummary job, String skill) {
        String quote = requirementQuote(job, skill);
        if (quote.matches("(?isu).*(优先|加分|preferred|nice to have|plus).*")) return "PREFERRED";
        if (quote.matches("(?isu).*(必须|必需|必备|要求|需具备|需要|熟悉|熟练|掌握|required|must|proficient).*")) return "REQUIRED";
        return "UNSPECIFIED";
    }

    private static void orderPrerequisites(String id, List<KnowledgeTopic> topics,
            Map<String, RecommendationReason> reasons, LinkedHashMap<String, RecommendationReason> ordered, Set<String> visiting) {
        if (ordered.containsKey(id) || !visiting.add(id)) return;
        KnowledgeTopic topic = topics.stream().filter(t -> id.equals(t.id())).findFirst().orElse(null);
        if (topic == null) return;
        for (String prerequisite : safe(topic.prerequisites())) {
            KnowledgeTopic prior = topics.stream().filter(t -> prerequisite.equals(t.id()) || SkillOntology.same(prerequisite, t.skill())).findFirst().orElse(null);
            if (prior != null) {
                reasons.putIfAbsent(prior.id(), new RecommendationReason("学习“" + topic.title() + "”的前置知识", "PREREQUISITE"));
                orderPrerequisites(prior.id(), topics, reasons, ordered, visiting);
            }
        }
        ordered.put(id, reasons.getOrDefault(id, new RecommendationReason("通用岗位建议", "GENERAL")));
        visiting.remove(id);
    }
    private static List<String> safe(List<String> values) { return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList(); }
    private Object lock(String id) { return locks[Math.floorMod(id.hashCode(), locks.length)]; }
    public record ReviewSchedule(int index, boolean enabled) {}
    public record PracticeSource(String documentId, int version) {}
    public record AttemptVersion(String algorithmVersion, String modelVersion) {}
    private record RecommendationReason(String reason, String gapType) {}
    private record QueryContext(RecruitmentContextClient.ValidatedContext recruitment, LearningPlan plan, InterviewSession interview) {}
    private static String fingerprint(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
