package com.aicampus.ai.service;

import com.aicampus.common.dto.AiModuleStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DashScopeClient {
    private static final String PROVIDER = "dashscope";
    private static final List<String> CAPABILITIES = List.of(
            "resume-analysis",
            "job-analysis",
            "match-analysis",
            "interview-question-generation",
            "interview-feedback",
            "candidate-screening");

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final RestClient restClient;

    public DashScopeClient(
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.model:qwen-plus}") String model,
            @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = valueOr(model, "qwen-plus");
        this.baseUrl = valueOr(baseUrl, "https://dashscope.aliyuncs.com/compatible-mode/v1");
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String complete(String systemPrompt, String userPrompt, boolean jsonResponse) {
        if (!isConfigured()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY is not configured");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (jsonResponse) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        Map<?, ?> response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(Map.class);
        return extractContent(response);
    }

    public AiModuleStatus status() {
        boolean configured = isConfigured();
        return new AiModuleStatus(
                PROVIDER,
                model,
                configured,
                baseUrl,
                CAPABILITIES,
                configured ? null : "DASHSCOPE_API_KEY is not configured");
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String extractContent(Map<?, ?> response) {
        if (response == null) {
            throw new IllegalStateException("DashScope response is empty");
        }
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> messageMap) {
                    Object content = messageMap.get("content");
                    if (content != null && !content.toString().isBlank()) {
                        return content.toString();
                    }
                }
            }
        }
        throw new IllegalStateException("DashScope response content is missing");
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
