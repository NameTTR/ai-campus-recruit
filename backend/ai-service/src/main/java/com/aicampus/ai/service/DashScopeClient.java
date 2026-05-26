package com.aicampus.ai.service;

import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.aicampus.common.dto.InterviewFeedback;
import com.aicampus.common.dto.InterviewFeedbackRequest;
import com.aicampus.common.dto.InterviewQuestion;
import com.aicampus.common.dto.InterviewQuestionRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DashScopeClient {
    private static final List<String> DEFAULT_SKILLS = List.of("Java", "Spring Boot", "MySQL");

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeClient(
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.model:qwen-plus}") String model,
            @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public AiAnalyzeResponse analyze(AiAnalyzeRequest request) {
        if (isMockMode()) {
            return mock(request);
        }
        try {
            String content = callDashScope(buildAnalyzePrompt(request));
            return new AiAnalyzeResponse(taskType(request), "dashscope", content, false);
        } catch (RuntimeException ex) {
            return mock(request);
        }
    }

    public List<InterviewQuestion> generateInterviewQuestions(InterviewQuestionRequest request) {
        if (isMockMode()) {
            return mockInterviewQuestions(request);
        }
        try {
            String content = callDashScope(buildInterviewQuestionPrompt(request));
            return parseInterviewQuestions(content, request);
        } catch (RuntimeException ex) {
            return mockInterviewQuestions(request);
        }
    }

    public InterviewFeedback generateInterviewFeedback(InterviewFeedbackRequest request) {
        if (isMockMode()) {
            return mockInterviewFeedback(request);
        }
        try {
            String content = callDashScope(buildInterviewFeedbackPrompt(request));
            return parseInterviewFeedback(content, request);
        } catch (RuntimeException ex) {
            return mockInterviewFeedback(request);
        }
    }

    private boolean isMockMode() {
        return apiKey == null || apiKey.isBlank();
    }

    private String callDashScope(String prompt) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是校园招聘平台的职业规划与面试辅导助手，输出简洁、结构化、可执行的中文建议。"),
                        Map.of("role", "user", "content", prompt)
                )
        );
        Map<?, ?> response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(Map.class);
        return extractContent(response);
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
                """.formatted(taskType(request), valueOr(request == null ? null : request.context(), "无"),
                valueOr(request == null ? null : request.content(), "无"));
    }

    private String buildInterviewQuestionPrompt(InterviewQuestionRequest request) {
        return """
                请为校园招聘候选人生成 3 道模拟面试题，只返回 JSON 数组。
                候选人：%s
                简历：%s
                岗位：%s
                目标岗位：%s
                技能：%s

                JSON 字段：
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
                请评价校园招聘模拟面试回答，只返回 JSON 对象。
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

    private String extractContent(Map<?, ?> response) {
        if (response == null) {
            return "AI 未返回内容。";
        }
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> messageMap) {
                    Object content = messageMap.get("content");
                    if (content != null) {
                        return content.toString();
                    }
                }
            }
        }
        return "AI 已完成分析，但响应格式未匹配预期。";
    }

    private List<InterviewQuestion> parseInterviewQuestions(String content, InterviewQuestionRequest request) {
        try {
            List<InterviewQuestion> questions = objectMapper.readValue(extractJson(content, '[', ']'),
                    new TypeReference<List<InterviewQuestion>>() {
                    });
            List<InterviewQuestion> normalized = normalizeQuestions(questions);
            if (normalized.size() >= 2) {
                return normalized;
            }
        } catch (Exception ignored) {
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(extractJson(content, '{', '}'),
                    new TypeReference<Map<String, Object>>() {
                    });
            List<InterviewQuestion> questions = objectMapper.convertValue(payload.get("questions"),
                    new TypeReference<List<InterviewQuestion>>() {
                    });
            List<InterviewQuestion> normalized = normalizeQuestions(questions);
            if (normalized.size() >= 2) {
                return normalized;
            }
        } catch (Exception ignored) {
        }
        return mockInterviewQuestions(request);
    }

    private InterviewFeedback parseInterviewFeedback(String content, InterviewFeedbackRequest request) {
        try {
            InterviewFeedback feedback = objectMapper.readValue(extractJson(content, '{', '}'), InterviewFeedback.class);
            if (feedback.score() > 0 && !safeList(feedback.suggestions(), List.of()).isEmpty()) {
                return new InterviewFeedback(
                        clamp(feedback.score()),
                        safeList(feedback.strengths(), List.of("回答覆盖了题目重点")),
                        safeList(feedback.gaps(), List.of("还可以补充更具体的项目数据")),
                        safeList(feedback.suggestions(), List.of("按 STAR 结构重新组织回答")),
                        valueOr(feedback.summary(), "回答已完成，可继续补充量化结果。"),
                        false);
            }
        } catch (Exception ignored) {
        }
        return mockInterviewFeedback(request);
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
                    safeList(question.referencePoints(), List.of("结合项目背景", "说明技术取舍", "补充结果指标"))));
        }
        return normalized;
    }

    private String extractJson(String content, char open, char close) {
        String trimmed = valueOr(content, "").trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf(open);
        int end = trimmed.lastIndexOf(close);
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private AiAnalyzeResponse mock(AiAnalyzeRequest request) {
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
                List.of("回答能围绕题目展开，具备基本岗位理解", "能够提到项目或技术关键词，便于面试官继续追问"),
                List.of("缺少可验证的数据结果", "技术取舍和个人贡献还不够具体"),
                List.of("按 STAR 结构补充背景、任务、行动和结果", "加入接口耗时、数据量、并发量等量化指标", "说明遇到的困难以及最终复盘"),
                "当前回答可以作为初稿，补充细节和结果后会更适合校园招聘面试。",
                true);
    }

    private static String taskType(AiAnalyzeRequest request) {
        return valueOr(request == null ? null : request.taskType(), "general");
    }

    private static String targetRole(InterviewQuestionRequest request) {
        return valueOr(request == null ? null : request.targetRole(), "Java 后端实习生");
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
