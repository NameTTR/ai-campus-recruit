package com.aicampus.ai.service;

import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.AiModuleStatus;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.aicampus.common.dto.InterviewRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class AiCoachService {
    private static final String SYSTEM_PROMPT =
            "你是校园招聘平台的职业规划与面试辅导助手，输出简洁、结构化、可执行的中文建议。";
    private static final List<String> DEFAULT_SKILLS = List.of("Java", "Spring Boot", "MySQL");
    private static final List<String> DEFAULT_STRENGTHS = List.of(
            "回答能围绕题目展开，具备基本岗位理解",
            "能够提到项目或技术关键词，便于面试官继续追问");
    private static final List<String> DEFAULT_GAPS = List.of(
            "缺少可验证的数据结果",
            "技术取舍和个人贡献还不够具体");
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "按 STAR 结构补充背景、任务、行动和结果",
            "加入接口耗时、数据量、并发量等量化指标",
            "说明遇到的困难以及最终复盘");
    private static final List<String> DEFAULT_PROJECTS = List.of("校园招聘平台", "简历诊断与岗位匹配模块");
    private static final List<String> DEFAULT_JOB_REQUIREMENTS = List.of("Java", "Spring Boot", "MySQL", "Redis");
    private static final List<String> DEFAULT_SCREEN_STRENGTHS = List.of(
            "Java 和 Spring Boot 基础与后端实习岗位匹配",
            "具备 MySQL 表设计、接口开发和基础排障经验",
            "了解 Redis 缓存思路，可继续追问缓存一致性和命中率");
    private static final List<String> DEFAULT_SCREEN_RISKS = List.of(
            "项目成果缺少接口耗时、数据量、并发量等量化指标",
            "MySQL 索引优化和 Redis 高并发场景需要面试确认",
            "个人贡献边界需要结合具体模块进一步核实");
    private static final List<String> DEFAULT_SCREEN_ACTIONS = List.of(
            "建议进入一面",
            "面试重点追问 Java 基础、Spring Boot 分层设计、MySQL 索引和 Redis 缓存",
            "要求候选人补充项目量化指标和个人负责模块");

    private final DashScopeClient dashScopeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<InterviewRecord>> interviewRecords = new ConcurrentHashMap<>();

    public AiCoachService(DashScopeClient dashScopeClient) {
        this.dashScopeClient = dashScopeClient;
    }

    public AiAnalyzeResponse analyze(AiAnalyzeRequest request) {
        if (!dashScopeClient.isConfigured()) {
            return mockAnalyze(request);
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, buildAnalyzePrompt(request), false);
            return new AiAnalyzeResponse(taskType(request), "dashscope", content, false);
        } catch (RuntimeException ex) {
            return mockAnalyze(request);
        }
    }

    public List<InterviewQuestion> generateInterviewQuestions(InterviewQuestionRequest request) {
        if (!dashScopeClient.isConfigured()) {
            return mockInterviewQuestions(request);
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, buildInterviewQuestionPrompt(request), true);
            List<InterviewQuestion> questions = parseInterviewQuestions(content);
            return questions.size() >= 3 ? questions : mockInterviewQuestions(request);
        } catch (RuntimeException ex) {
            return mockInterviewQuestions(request);
        }
    }

    public InterviewFeedback generateInterviewFeedback(InterviewFeedbackRequest request) {
        InterviewFeedback feedback;
        if (!dashScopeClient.isConfigured()) {
            feedback = mockInterviewFeedback(request);
        } else {
            try {
                String content = dashScopeClient.complete(SYSTEM_PROMPT, buildInterviewFeedbackPrompt(request), true);
                feedback = parseInterviewFeedback(content);
            } catch (RuntimeException ex) {
                feedback = mockInterviewFeedback(request);
            }
        }
        saveInterviewRecord(request, feedback);
        return feedback;
    }

    public CandidateScreenResult screenCandidate(CandidateScreenRequest request) {
        if (!dashScopeClient.isConfigured()) {
            return mockCandidateScreen(request);
        }
        try {
            String content = dashScopeClient.complete(SYSTEM_PROMPT, buildCandidateScreenPrompt(request), true);
            return parseCandidateScreenResult(content, request);
        } catch (RuntimeException ex) {
            return mockCandidateScreen(request);
        }
    }

    public AiModuleStatus status() {
        return dashScopeClient.status();
    }

    public List<InterviewRecord> listInterviewRecords(String studentId) {
        String key = valueOr(studentId, "");
        return interviewRecords.getOrDefault(key, List.of()).stream()
                .sorted(Comparator.comparing(InterviewRecord::createdAt).reversed())
                .toList();
    }

    private void saveInterviewRecord(InterviewFeedbackRequest request, InterviewFeedback feedback) {
        String studentId = valueOr(request == null ? null : request.studentId(), "S001");
        InterviewRecord record = new InterviewRecord(
                "IR-" + UUID.randomUUID(),
                studentId,
                valueOr(request == null ? null : request.targetRole(), "未知岗位"),
                valueOr(request == null ? null : request.questionId(), "IQ-UNKNOWN"),
                valueOr(request == null ? null : request.question(), ""),
                valueOr(request == null ? null : request.answer(), ""),
                feedback.score(),
                valueOr(feedback.summary(), ""),
                safeList(feedback.suggestions(), DEFAULT_SUGGESTIONS),
                feedback.mocked(),
                Instant.now());
        interviewRecords.computeIfAbsent(studentId, ignored -> new CopyOnWriteArrayList<>()).add(record);
    }

    private String buildAnalyzePrompt(AiAnalyzeRequest request) {
        return """
                任务类型：%s
                业务上下文：%s
                待分析内容：%s

                请返回：
                1. 核心判断
                2. 优势
                3. 短板
                4. 三条可执行建议
                """.formatted(
                taskType(request),
                valueOr(request == null ? null : request.context(), "无"),
                valueOr(request == null ? null : request.content(), "无"));
    }

    private String buildInterviewQuestionPrompt(InterviewQuestionRequest request) {
        return """
                请为校园招聘候选人生成 3 道模拟面试题。
                只返回 JSON 对象，格式为：{"questions": [...]}。

                候选人：%s
                简历：%s
                岗位：%s
                目标岗位：%s
                技能：%s

                每项必须包含：
                - questionId: 字符串
                - category: 技术基础、项目深挖、行为面试之一
                - difficulty: 基础、中等、进阶之一
                - question: 面试题正文
                - referencePoints: 字符串数组，2 到 4 个答题要点
                """.formatted(
                valueOr(request == null ? null : request.studentId(), "S001"),
                valueOr(request == null ? null : request.resumeId(), "R001"),
                valueOr(request == null ? null : request.jobId(), "J001"),
                targetRole(request),
                String.join("、", safeList(request == null ? null : request.skills(), DEFAULT_SKILLS)));
    }

    private String buildInterviewFeedbackPrompt(InterviewFeedbackRequest request) {
        return """
                请评价校园招聘模拟面试回答。
                只返回 JSON 对象。

                候选人：%s
                目标岗位：%s
                题目编号：%s
                题目：%s
                回答：%s

                JSON 字段：
                - score: 0 到 100 的整数
                - strengths: 字符串数组，2 到 3 条
                - gaps: 字符串数组，2 到 3 条
                - suggestions: 字符串数组，2 到 4 条
                - summary: 一句话总结
                - mocked: false
                """.formatted(
                valueOr(request == null ? null : request.studentId(), "S001"),
                valueOr(request == null ? null : request.targetRole(), "Java 后端实习生"),
                valueOr(request == null ? null : request.questionId(), "IQ-001"),
                valueOr(request == null ? null : request.question(), "请结合项目经历说明技术方案。"),
                valueOr(request == null ? null : request.answer(), "无"));
    }

    private String buildCandidateScreenPrompt(CandidateScreenRequest request) {
        return """
                请基于简历摘要、项目经历和岗位要求完成校园招聘候选人初筛。
                只返回 JSON 对象，不要返回 Markdown 或额外解释。

                JSON 字段：
                - deliveryId: 字符串
                - studentId: 字符串
                - jobId: 字符串
                - score: 0 到 100 的整数
                - recommendation: 一句话筛选建议
                - strengths: 字符串数组，2 到 4 条
                - risks: 字符串数组，2 到 4 条
                - interviewQuestions: 字符串数组，2 到 4 条
                - nextActions: 字符串数组，2 到 4 条
                - mocked: false

                投递编号：%s
                学生编号：%s
                简历编号：%s
                岗位编号：%s
                目标岗位：%s
                技能：%s
                项目经历：%s
                岗位要求：%s
                简历摘要：%s
                岗位描述：%s
                """.formatted(
                deliveryId(request),
                studentId(request),
                valueOr(request == null ? null : request.resumeId(), "R001"),
                jobId(request),
                targetRole(request),
                String.join("、", safeList(request == null ? null : request.skills(), DEFAULT_SKILLS)),
                String.join("；", safeList(request == null ? null : request.projects(), DEFAULT_PROJECTS)),
                String.join("、", safeList(request == null ? null : request.jobRequirements(), DEFAULT_JOB_REQUIREMENTS)),
                valueOr(request == null ? null : request.resumeSummary(), "具备 Java 后端基础和校园项目经验"),
                valueOr(request == null ? null : request.jobDescription(), "负责 Java 后端接口开发、数据库设计和缓存优化"));
    }

    private List<InterviewQuestion> parseInterviewQuestions(String content) {
        JsonNode root = readJson(content);
        JsonNode questionNode = root.isArray() ? root : root.get("questions");
        if (questionNode == null || !questionNode.isArray()) {
            return List.of();
        }

        List<InterviewQuestion> questions = objectMapper.convertValue(
                questionNode, new TypeReference<List<InterviewQuestion>>() {
                });
        return normalizeQuestions(questions);
    }

    private InterviewFeedback parseInterviewFeedback(String content) {
        JsonNode root = readJson(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Feedback response is not a JSON object");
        }
        int score = readScore(root.get("score"), 70);
        List<String> strengths = readStringList(root.get("strengths"), DEFAULT_STRENGTHS);
        List<String> gaps = readStringList(root.get("gaps"), DEFAULT_GAPS);
        List<String> suggestions = readStringList(root.get("suggestions"), DEFAULT_SUGGESTIONS);
        String summary = textOr(root.get("summary"), "回答可以作为初稿，补充细节和结果后会更完整。");
        return new InterviewFeedback(score, strengths, gaps, suggestions, summary, false);
    }

    private CandidateScreenResult parseCandidateScreenResult(String content, CandidateScreenRequest request) {
        JsonNode root = readJson(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Candidate screen response is not a JSON object");
        }
        JsonNode result = root.has("result") && root.get("result").isObject() ? root.get("result") : root;
        CandidateScreenResult fallback = mockCandidateScreen(request);
        return new CandidateScreenResult(
                textOr(result.get("deliveryId"), fallback.deliveryId()),
                textOr(result.get("studentId"), fallback.studentId()),
                textOr(result.get("jobId"), fallback.jobId()),
                readScore(result.get("score"), fallback.score()),
                textOr(result.get("recommendation"), fallback.recommendation()),
                readStringList(result.get("strengths"), fallback.strengths()),
                readStringList(result.get("risks"), fallback.risks()),
                readStringList(result.get("interviewQuestions"), fallback.interviewQuestions()),
                readStringList(result.get("nextActions"), fallback.nextActions()),
                false);
    }

    private JsonNode readJson(String content) {
        try {
            return objectMapper.readTree(extractJson(content));
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI response is not valid JSON", ex);
        }
    }

    private List<InterviewQuestion> normalizeQuestions(List<InterviewQuestion> questions) {
        if (questions == null) {
            return List.of();
        }
        List<InterviewQuestion> normalized = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (question == null || question.question() == null || question.question().isBlank()) {
                continue;
            }
            int index = normalized.size() + 1;
            normalized.add(new InterviewQuestion(
                    valueOr(question.questionId(), "IQ-AI-" + index),
                    valueOr(question.category(), "综合面试"),
                    valueOr(question.difficulty(), "中等"),
                    question.question(),
                    safeList(question.referencePoints(), List.of(
                            "结合项目背景",
                            "说明技术取舍",
                            "补充结果指标"))));
        }
        return normalized;
    }

    private String extractJson(String content) {
        String trimmed = valueOr(content, "").trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }

        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int start;
        char close;
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            start = objectStart;
            close = '}';
        } else if (arrayStart >= 0) {
            start = arrayStart;
            close = ']';
        } else {
            return trimmed;
        }

        int end = trimmed.lastIndexOf(close);
        if (end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private AiAnalyzeResponse mockAnalyze(AiAnalyzeRequest request) {
        String type = taskType(request);
        String content = switch (type) {
            case "resume" -> "简历基础较完整，Java、Spring Boot、数据库能力与后端岗位匹配。建议补充项目规模、性能指标、部署方式和个人职责。";
            case "job" -> "岗位偏 Java 后端工程实践，重点关注 Spring Boot、MySQL、Redis、接口设计和团队协作能力。";
            case "match" -> "候选人与岗位匹配度较高，优势在 Java Web 技术栈，短板是企业级微服务和消息队列经验需要补强。";
            default -> "已生成演示分析结果。建议补充结构化信息以提高 AI 判断质量。";
        };
        return new AiAnalyzeResponse(type, "mock-dashscope", content, true);
    }

    private List<InterviewQuestion> mockInterviewQuestions(InterviewQuestionRequest request) {
        String role = targetRole(request);
        List<String> skills = safeList(request == null ? null : request.skills(), DEFAULT_SKILLS);
        String primarySkill = skills.get(0);
        String secondarySkill = skills.size() > 1 ? skills.get(1) : primarySkill;
        return List.of(
                new InterviewQuestion("IQ-001", "项目深挖", "中等",
                        "请结合一个项目说明你如何使用 " + primarySkill + " 解决核心业务问题，并说明你的个人贡献。",
                        List.of("项目背景和业务目标", "关键技术方案与取舍", "个人负责的模块", "可量化结果或复盘")),
                new InterviewQuestion("IQ-002", "技术基础", "中等",
                        "面向 " + role + " 岗位，如果接口响应变慢，你会如何从应用、数据库和缓存三个层面排查？",
                        List.of("先确认监控和日志", "分析 SQL 与索引", "检查缓存命中率", "说明压测或复现方式")),
                new InterviewQuestion("IQ-003", "行为面试", "基础",
                        "请讲一次你在团队协作中推动问题解决的经历，并说明如何和同学或业务方沟通。",
                        List.of("使用 STAR 结构", "说明冲突或阻塞点", "突出沟通动作", "总结经验迁移到 " + secondarySkill + " 相关项目")));
    }

    private InterviewFeedback mockInterviewFeedback(InterviewFeedbackRequest request) {
        String answer = valueOr(request == null ? null : request.answer(), "");
        int score = answer.trim().length() >= 80 ? 84 : answer.trim().length() >= 30 ? 76 : 68;
        return new InterviewFeedback(
                score,
                DEFAULT_STRENGTHS,
                DEFAULT_GAPS,
                DEFAULT_SUGGESTIONS,
                "当前回答可以作为初稿，补充细节和结果后会更适合校园招聘面试。",
                true);
    }

    private CandidateScreenResult mockCandidateScreen(CandidateScreenRequest request) {
        List<String> skills = safeList(request == null ? null : request.skills(), DEFAULT_SKILLS);
        String primarySkill = skills.get(0);
        List<String> interviewQuestions = List.of(
                "请说明一个 " + primarySkill + " 项目中你负责的核心模块、接口设计和异常处理方式。",
                "如果接口响应变慢，你会如何结合日志、MySQL 索引和 Redis 缓存定位问题？",
                "请补充项目上线或测试中的数据量、并发量、耗时变化以及你的个人贡献。");
        return new CandidateScreenResult(
                deliveryId(request),
                studentId(request),
                jobId(request),
                86,
                "建议进入一面",
                DEFAULT_SCREEN_STRENGTHS,
                DEFAULT_SCREEN_RISKS,
                interviewQuestions,
                DEFAULT_SCREEN_ACTIONS,
                true);
    }

    private List<String> readStringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && !item.isContainerNode() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private int readScore(JsonNode node, int fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.canConvertToInt()) {
            return clamp(node.asInt());
        }
        if (node.isTextual()) {
            try {
                return clamp(Integer.parseInt(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String textOr(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        return valueOr(node.asText(), fallback);
    }

    private static String taskType(AiAnalyzeRequest request) {
        return valueOr(request == null ? null : request.taskType(), "general");
    }

    private static String targetRole(InterviewQuestionRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java 后端实习生");
    }

    private static String targetRole(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java 后端实习生");
    }

    private static String deliveryId(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.deliveryId(), "D001");
    }

    private static String studentId(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.studentId(), "S001");
    }

    private static String jobId(CandidateScreenRequest request) {
        return valueOr(request == null ? null : request.jobId(), "J001");
    }

    private static List<String> safeList(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<String> filtered = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return filtered.isEmpty() ? fallback : filtered;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
