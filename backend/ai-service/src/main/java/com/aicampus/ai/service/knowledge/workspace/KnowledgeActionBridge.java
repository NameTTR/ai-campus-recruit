package com.aicampus.ai.service.knowledge.workspace;

import com.aicampus.ai.service.core.AiCareerCoreService;
import com.aicampus.ai.service.core.LearningPlanStore;
import com.aicampus.ai.service.core.RecruitmentContextClient;
import com.aicampus.common.dto.*;
import static com.aicampus.common.dto.KnowledgeWorkspaceModels.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Creates only reviewable drafts; changes take effect through a separate confirmation. */
@Component
public class KnowledgeActionBridge {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final LearningPlanStore plans;
    private final AiCareerCoreService career;
    private final RecruitmentContextClient contexts;
    private final Object[] locks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public KnowledgeActionBridge(LearningPlanStore plans, AiCareerCoreService career, RecruitmentContextClient contexts) {
        this.plans = plans; this.career = career; this.contexts = contexts;
    }

    public Map<String, Object> preview(String student, String role, KnowledgeActionRequest request, KnowledgeTopic topic) {
        if (request == null) throw new IllegalArgumentException("请选择后续行动");
        var context = contexts.validate(student, request.resumeId(), request.jobId(), request.matchId(), role);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resumeId", text(request.resumeId())); payload.put("jobId", text(request.jobId()));
        payload.put("matchId", text(request.matchId()));
        payload.put("targetRole", target(request.targetRole(), topic));
        payload.put("topicVersion", topic.version());
        payload.put("exercise", topic.practicePrompt());
        payload.put("acceptanceCriteria", List.of("提交实际操作说明、验证步骤与结果", "对照引用资料解释自己的方法"));
        payload.put("sourceReference", Map.of("documentId", topic.documentId(), "title", topic.title(),
                "source", topic.source(), "version", topic.version(), "snippet", topic.practicePrompt()));
        if ("INTERVIEW".equals(request.type())) {
            payload.put("sourceMaterial", topic.content() + "\n" + Objects.toString(topic.example(), "")
                    + "\n" + Objects.toString(topic.practicePrompt(), ""));
            payload.put("impact", "确认后开始 3 道辅导练习，不修改简历或学习计划。");
            return payload;
        }
        if (!"LEARNING_PLAN".equals(request.type())) throw new IllegalArgumentException("不支持的后续行动");
        LearningPlan base = text(request.planId()).isBlank() ? null : career.getLearningPlan(request.planId(), student);
        if (base != null && !"ACTIVE".equals(base.status())) throw new IllegalArgumentException("请选择当前有效的学习计划");
        if (base != null && !text(request.jobId()).isBlank() && !text(base.jobId()).isBlank()
                && !request.jobId().equals(base.jobId()))
            throw new IllegalArgumentException("学习计划与当前岗位不一致，请选择对应计划");
        int hours = range(request.weeklyHours(), base == null ? 2 : base.weeklyHours(), 2, 40, "每周小时数");
        int weeks = range(request.durationWeeks(), base == null ? 1 : base.durationWeeks(), 1, 24, "计划周数");
        int cap = range(request.dailyMinutesCap(), base == null || base.dailyMinutesCap() <= 0 ? 60 : base.dailyMinutesCap(), 30, 480, "每日分钟上限");
        List<String> studyDays = request.studyDays() == null || request.studyDays().isEmpty()
                ? base == null || base.studyDays() == null || base.studyDays().isEmpty()
                    ? List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY") : base.studyDays()
                : request.studyDays().stream().distinct().toList();
        Set<DayOfWeek> days = new HashSet<>();
        try { studyDays.forEach(d -> days.add(DayOfWeek.valueOf(d))); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("请选择有效学习日"); }
        LocalDate start = parseDate(request.startDate(), base == null ? nextDay(days) : parseDate(base.startDate(), nextDay(days)));
        if (base != null && !text(base.startDate()).isBlank() && !start.toString().equals(base.startDate()))
            throw new IllegalArgumentException("请先在学习路径调整开始日期，再重新预览知识练习");
        List<LearningTask> tasks = base == null ? new ArrayList<>() : new ArrayList<>(base.tasks());
        int minutes = Math.max(10, topic.estimatedMinutes());
        Map<String, Integer> daily = new HashMap<>();
        Map<Integer, Integer> weekly = new HashMap<>();
        for (LearningTask task : tasks) {
            if (task.week() > weeks) throw new IllegalArgumentException("缩短周期会丢失已有任务，请保留原周期");
            int amount = Math.max(0, task.estimatedMinutes());
            if (amount == 0) amount = Math.max(0, task.estimatedHours()) * 60;
            LocalDate date = parseDate(task.taskDate(), start.plusWeeks(Math.max(0, task.week() - 1)));
            if (!days.contains(date.getDayOfWeek())) throw new IllegalArgumentException("已有任务不在所选学习日，请先在学习路径调整日程");
            daily.merge(date.toString(), amount, Integer::sum);
            weekly.merge(task.week(), amount, Integer::sum);
        }
        if (daily.values().stream().anyMatch(v -> v > cap) || weekly.values().stream().anyMatch(v -> v > hours * 60))
            throw new IllegalArgumentException("调整后的预算低于已有任务，请增加时间预算或先调整原计划");
        LocalDate slot = null; int slotWeek = 0;
        for (int offset = 0; offset < weeks * 7; offset++) {
            LocalDate candidate = start.plusDays(offset); int week = offset / 7 + 1;
            if (candidate.isBefore(LocalDate.now(ZONE)) || !days.contains(candidate.getDayOfWeek())) continue;
            if (daily.getOrDefault(candidate.toString(), 0) + minutes <= cap && weekly.getOrDefault(week, 0) + minutes <= hours * 60) {
                slot = candidate; slotWeek = week; break;
            }
        }
        if (slot == null) throw new IllegalArgumentException("当前日程无法加入练习，请增加计划周数或调整可投入时间后重新预览");
        String input = digest(student + request + topic.id() + topic.version() + start + studyDays + hours + weeks + cap
                + (base == null ? "" : base.updatedAt()));
        String draftId = "LP-KB-" + input.substring(0, 20);
        String taskId = "TASK-KB-" + input.substring(0, 20);
        var ref = new LearningReference(topic.documentId(), topic.title(), topic.source(), topic.practicePrompt());
        tasks.add(new LearningTask(taskId, slotWeek, topic.title() + "实践", topic.practicePrompt(), topic.skill(),
                "PRACTICE", "提交实际操作说明、验证步骤与结果；对照引用资料解释自己的方法。",
                "实践说明与测试或验证记录", (minutes + 59) / 60, "PENDING", null, null, Instant.now(),
                List.of(), List.of(ref), "VERIFIED_SOURCE", List.of(), slot.toString(), minutes, List.of(),
                "KNOWLEDGE:" + topic.id() + ":" + topic.version(), null, false, null));
        Instant now = Instant.now();
        LearningPlan draft = new LearningPlan(draftId, base == null ? draftId : base.rootPlanId(), student,
                base == null ? request.resumeId() : base.resumeId(), base == null ? request.jobId() : base.jobId(),
                base == null ? request.matchId() : base.matchId(), base == null ? target(request.targetRole(), topic) : base.targetRole(),
                base == null ? context.snapshot(request.resumeId(), request.jobId(), request.matchId()) : base.contextSnapshot(),
                hours, weeks, start.toString(), studyDays, cap, "DRAFT", base == null ? 1 : base.version() + 1,
                base == null ? null : base.planId(), tasks, false, now, now, "加入知识专题实践：" + topic.title(),
                new AnalysisMetadata(input, "knowledge-actions-v1", "RULES", "authored-practice-v1", "KNOWLEDGE", now));
        synchronized (lock(draftId)) {
            if (plans.findById(draftId).isEmpty()) plans.save(draft);
        }
        payload.put("planId", base == null ? "" : base.planId()); payload.put("revisionId", draftId);
        payload.put("baseUpdatedAt", base == null ? "" : base.updatedAt().toString());
        payload.put("taskDate", slot.toString()); payload.put("weeklyHours", hours); payload.put("durationWeeks", weeks);
        payload.put("dailyMinutesCap", cap); payload.put("taskId", taskId);
        payload.put("impact", "新增 " + minutes + " 分钟实践，安排在 " + slot + "；已有任务和成果保留。");
        return payload;
    }

    public Map<String, Object> confirm(String student, String role, KnowledgeActionPreview preview) {
        synchronized (lock(preview.previewId())) {
            Map<String, Object> payload = new LinkedHashMap<>(preview.payload());
            if ("INTERVIEW".equals(preview.type())) {
                String sourceId = "KNOWLEDGE:" + preview.topicId();
                String sessionId = "IS-KB-" + digest(student + preview.previewId()).substring(0, 20);
                String material = Objects.toString(payload.get("sourceMaterial"), "");
                if (material.isBlank()) throw new IllegalArgumentException("资料依据已失效，请重新预览");
                var request = new InterviewSessionCreateRequest(student, nullable(payload.get("resumeId")),
                        nullable(payload.get("jobId")), nullable(payload.get("matchId")), nullable(payload.get("targetRole")),
                        3, "COACHING", "GAP", sourceId, null);
                var session = career.createKnowledgeInterview(student, role, request, sourceId, material,
                        List.of(new InterviewSourceReference(sourceId, "KNOWLEDGE", material,
                                "topic/content/v" + payload.get("topicVersion"))),
                        List.of(preview.title()), sessionId);
                payload.put("sessionId", session.sessionId()); payload.put("path", "/student/interview/practice?sessionId=" + session.sessionId());
                return payload;
            }
            String revision = Objects.toString(payload.get("revisionId"), "");
            career.validateKnowledgeDraft(revision, student);
            LearningPlan draft = career.getLearningPlan(revision, student);
            if ("ACTIVE".equals(draft.status())) { payload.put("path", "/student/plan/tasks?planId=" + revision); return payload; }
            if (!"DRAFT".equals(draft.status())) throw new IllegalArgumentException("该预览已失效，请重新生成");
            String baseId = Objects.toString(payload.get("planId"), "");
            if (!baseId.isBlank()) {
                LearningPlan base = career.getLearningPlan(baseId, student);
                if (!base.updatedAt().toString().equals(Objects.toString(payload.get("baseUpdatedAt"), "")))
                    throw new IllegalArgumentException("原计划已发生变化，请重新预览");
                career.confirmLearningRevision(baseId, student, revision);
            } else {
                plans.save(new LearningPlan(draft.planId(), draft.rootPlanId(), draft.studentId(), draft.resumeId(),
                        draft.jobId(), draft.matchId(), draft.targetRole(), draft.contextSnapshot(), draft.weeklyHours(),
                        draft.durationWeeks(), draft.startDate(), draft.studyDays(), draft.dailyMinutesCap(), "ACTIVE",
                        draft.version(), null, draft.tasks(), draft.mocked(), draft.createdAt(), Instant.now(),
                        draft.revisionReason(), draft.analysisMetadata()));
            }
            payload.put("path", "/student/plan/tasks?planId=" + revision);
            return payload;
        }
    }

    private Object lock(String key) { return locks[Math.floorMod(key.hashCode(), locks.length)]; }
    private static int range(Integer value, int defaultValue, int min, int max, String name) {
        int result = value == null ? defaultValue : value;
        if (result < min || result > max) throw new IllegalArgumentException(name + "需在 " + min + " 到 " + max + " 之间");
        return result;
    }
    private static LocalDate nextDay(Set<DayOfWeek> days) {
        LocalDate date = LocalDate.now(ZONE).plusDays(1);
        while (!days.contains(date.getDayOfWeek())) date = date.plusDays(1);
        return date;
    }
    private static LocalDate parseDate(String value, LocalDate fallback) {
        try { return text(value).isBlank() ? fallback : LocalDate.parse(value); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("开始日期格式不正确"); }
    }
    private static String target(String requested, KnowledgeTopic topic) {
        return text(requested).isBlank() ? switch (topic.role()) {
            case "JAVA" -> "Java 实习生"; case "FRONTEND" -> "前端实习生"; case "OPERATIONS" -> "运营实习生";
            default -> topic.title();
        } : requested.trim();
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String nullable(Object value) { String text = Objects.toString(value, "").trim(); return text.isEmpty() ? null : text; }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
