package com.aicampus.ai.service;

import com.aicampus.common.dto.AiAnalyzeRequest;
import com.aicampus.common.dto.AiAnalyzeResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DashScopeClient {
    private final String apiKey;
    private final String model;
    private final RestClient restClient;

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
        if (apiKey == null || apiKey.isBlank()) {
            return mock(request);
        }
        try {
            String content = callDashScope(request);
            return new AiAnalyzeResponse(request.taskType(), "dashscope", content, false);
        } catch (RuntimeException ex) {
            return mock(request);
        }
    }

    private String callDashScope(AiAnalyzeRequest request) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是校园招聘平台的职业规划与招聘匹配助手，输出简洁、结构化、可执行的中文建议。"),
                        Map.of("role", "user", "content", buildPrompt(request))
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

    private String buildPrompt(AiAnalyzeRequest request) {
        return """
                任务类型：%s
                业务上下文：%s
                待分析内容：%s

                请返回：
                1. 核心判断
                2. 优势
                3. 短板
                4. 三条可执行建议
                """.formatted(request.taskType(), request.context(), request.content());
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

    private AiAnalyzeResponse mock(AiAnalyzeRequest request) {
        String content = switch (request.taskType()) {
            case "resume" -> "简历基础较完整，Java、Spring Boot、数据库能力与后端岗位匹配。建议补充项目规模、性能指标、部署方式和个人职责。";
            case "job" -> "岗位偏 Java 后端工程实践，重点关注 Spring Boot、MySQL、Redis、接口设计和团队协作能力。";
            case "match" -> "候选人与岗位匹配度较高，优势在 Java Web 技术栈，短板是企业级微服务和消息队列经验需要补强。";
            default -> "已生成演示分析结果。建议补充结构化信息以提高 AI 判断质量。";
        };
        return new AiAnalyzeResponse(request.taskType(), "mock-dashscope", content, true);
    }
}

