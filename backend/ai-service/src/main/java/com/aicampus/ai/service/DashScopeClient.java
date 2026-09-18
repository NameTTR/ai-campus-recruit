package com.aicampus.ai.service;

import com.aicampus.common.dto.AiModuleStatus;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DashScopeClient {
    private static final String PROVIDER = "dashscope";
    private static final List<String> CAPABILITIES = List.of(
            "resume-analysis",
            "job-analysis",
            "match-analysis",
            "resume-rewrite",
            "career-planning",
            "planning-history",
            "coach-advice",
            "interview-question-generation",
            "interview-feedback",
            "candidate-screening",
            "observability",
            "intelligent-search",
            "rag-retrieval",
            "rag-answering");

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final double temperature;
    private final int maxTokens;
    private final RestClient restClient;

    @Autowired
    public DashScopeClient(
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.model:qwen-plus}") String model,
            @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${dashscope.temperature:0.2}") double temperature,
            @Value("${dashscope.max-tokens:8192}") int maxTokens,
            @Value("${dashscope.connect-timeout:10s}") Duration connectTimeout,
            @Value("${dashscope.read-timeout:120s}") Duration readTimeout) {
        this.apiKey = apiKey;
        this.model = valueOr(model, "qwen-plus");
        this.baseUrl = valueOr(baseUrl, "https://dashscope.aliyuncs.com/compatible-mode/v1");
        this.temperature = clampTemperature(temperature);
        this.maxTokens = normalizeMaxTokens(maxTokens);
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
    }

    public DashScopeClient(String apiKey, String model, String baseUrl) {
        this(apiKey, model, baseUrl, 0.2, 8192);
    }

    public DashScopeClient(String apiKey, String model, String baseUrl, double temperature) {
        this(apiKey, model, baseUrl, temperature, 8192);
    }

    public DashScopeClient(String apiKey, String model, String baseUrl, double temperature, int maxTokens) {
        this(apiKey, model, baseUrl, temperature, maxTokens, Duration.ofSeconds(10), Duration.ofSeconds(120));
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
        payload.put("temperature", temperature);
        if (maxTokens > 0) {
            payload.put("max_tokens", maxTokens);
        }
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

    private static double clampTemperature(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.2;
        }
        return Math.max(0, Math.min(1, value));
    }

    private static int normalizeMaxTokens(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, 32768);
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMilliseconds(connectTimeout, "connect timeout"));
        requestFactory.setReadTimeout(toMilliseconds(readTimeout, "read timeout"));
        return requestFactory;
    }

    private static int toMilliseconds(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("DashScope " + name + " must be positive");
        }
        long milliseconds = duration.toMillis();
        if (milliseconds == 0 || milliseconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("DashScope " + name + " is out of range");
        }
        return (int) milliseconds;
    }
}
