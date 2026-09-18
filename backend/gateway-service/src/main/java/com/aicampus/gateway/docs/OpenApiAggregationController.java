package com.aicampus.gateway.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class OpenApiAggregationController {
    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");

    @Value("${AUTH_SERVICE_URI:http://localhost:8101}")
    private String authServiceUri = "http://localhost:8101";

    @Value("${USER_SERVICE_URI:http://localhost:8102}")
    private String userServiceUri = "http://localhost:8102";

    @Value("${RESUME_SERVICE_URI:http://localhost:8103}")
    private String resumeServiceUri = "http://localhost:8103";

    @Value("${JOB_SERVICE_URI:http://localhost:8104}")
    private String jobServiceUri = "http://localhost:8104";

    @Value("${MATCH_SERVICE_URI:http://localhost:8105}")
    private String matchServiceUri = "http://localhost:8105";

    @Value("${AI_SERVICE_URI:http://localhost:8106}")
    private String aiServiceUri = "http://localhost:8106";

    @Value("${DELIVERY_SERVICE_URI:http://localhost:8107}")
    private String deliveryServiceUri = "http://localhost:8107";

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenApiAggregationController() {
        this(new ObjectMapper(), WebClient.builder().build());
    }

    OpenApiAggregationController(ObjectMapper objectMapper, WebClient webClient) {
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    @GetMapping("/v3/api-docs/swagger-config")
    public Mono<SwaggerConfig> swaggerConfig(ServerHttpRequest request) {
        String baseUrl = request.getURI().getScheme() + "://" + request.getHeaders().getHost();
        return Mono.just(new SwaggerConfig(
                "/v3/api-docs/swagger-config",
                baseUrl + "/swagger-ui/oauth2-redirect.html",
                "/v3/api-docs",
                "",
                openApiUrls()));
    }

    @GetMapping("/v3/api-docs")
    public Mono<ObjectNode> aggregateOpenApi(ServerHttpRequest request) {
        List<OpenApiSource> sources = openApiSources();
        return Flux.fromIterable(sources)
                .flatMap(source -> fetchOpenApi(source)
                        .map(document -> OpenApiDocument.succeeded(source, document))
                        .onErrorResume(ex -> Mono.just(OpenApiDocument.failed(source, ex.getMessage()))))
                .collectList()
                .map(documents -> mergeOpenApi(request, documents));
    }

    private List<OpenApiUrl> openApiUrls() {
        return List.of(
                new OpenApiUrl("全部接口（网关聚合）", "/v3/api-docs"),
                new OpenApiUrl("认证服务 auth-service", "/v3/api-docs/auth-service"),
                new OpenApiUrl("用户与管理服务 user-service", "/v3/api-docs/user-service"),
                new OpenApiUrl("简历服务 resume-service", "/v3/api-docs/resume-service"),
                new OpenApiUrl("岗位服务 job-service", "/v3/api-docs/job-service"),
                new OpenApiUrl("匹配服务 match-service", "/v3/api-docs/match-service"),
                new OpenApiUrl("AI 与 RAG 服务 ai-service", "/v3/api-docs/ai-service"),
                new OpenApiUrl("投递通知服务 delivery-service", "/v3/api-docs/delivery-service"));
    }

    private List<OpenApiSource> openApiSources() {
        return List.of(
                new OpenApiSource("auth-service", "认证服务 auth-service", authServiceUri),
                new OpenApiSource("user-service", "用户与管理服务 user-service", userServiceUri),
                new OpenApiSource("resume-service", "简历服务 resume-service", resumeServiceUri),
                new OpenApiSource("job-service", "岗位服务 job-service", jobServiceUri),
                new OpenApiSource("match-service", "匹配服务 match-service", matchServiceUri),
                new OpenApiSource("ai-service", "AI 与 RAG 服务 ai-service", aiServiceUri),
                new OpenApiSource("delivery-service", "投递通知服务 delivery-service", deliveryServiceUri));
    }

    private Mono<ObjectNode> fetchOpenApi(OpenApiSource source) {
        String endpoint = UriComponentsBuilder.fromUriString(trimTrailingSlash(source.serviceUri()))
                .path("/v3/api-docs")
                .build()
                .toUriString();
        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(ObjectNode.class)
                .timeout(Duration.ofSeconds(10));
    }

    private ObjectNode mergeOpenApi(ServerHttpRequest request, List<OpenApiDocument> documents) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.0.1");
        ObjectNode info = root.putObject("info");
        info.put("title", "AI 校园招聘平台聚合接口文档");
        info.put("description", "通过 gateway-service 聚合展示所有微服务接口。");
        info.put("version", "0.1.0-SNAPSHOT");

        ArrayNode servers = root.putArray("servers");
        ObjectNode gatewayServer = servers.addObject();
        gatewayServer.put("url", request.getURI().getScheme() + "://" + request.getHeaders().getHost());
        gatewayServer.put("description", "gateway-service");

        ArrayNode tags = root.putArray("tags");
        ObjectNode paths = root.putObject("paths");
        ObjectNode components = root.putObject("components");
        ArrayNode serviceErrors = root.putArray("x-service-errors");

        for (OpenApiDocument document : documents) {
            ObjectNode tag = tags.addObject();
            tag.put("name", document.source().displayName());
            tag.put("description", document.source().id());
            if (document.failed()) {
                ObjectNode error = serviceErrors.addObject();
                error.put("service", document.source().id());
                error.put("message", document.errorMessage());
                continue;
            }
            mergePaths(paths, document.source(), document.document().path("paths"));
            mergeComponents(components, document.document().path("components"));
        }
        return root;
    }

    private void mergePaths(ObjectNode targetPaths, OpenApiSource source, JsonNode sourcePaths) {
        if (!sourcePaths.isObject()) {
            return;
        }
        sourcePaths.fields().forEachRemaining(pathEntry -> {
            JsonNode copied = pathEntry.getValue().deepCopy();
            if (copied instanceof ObjectNode pathItem) {
                tagOperations(source, pathItem);
            }
            targetPaths.set(pathEntry.getKey(), copied);
        });
    }

    private void tagOperations(OpenApiSource source, ObjectNode pathItem) {
        pathItem.fields().forEachRemaining(operationEntry -> {
            String method = operationEntry.getKey().toLowerCase(Locale.ROOT);
            JsonNode operationNode = operationEntry.getValue();
            if (!HTTP_METHODS.contains(method) || !(operationNode instanceof ObjectNode operation)) {
                return;
            }
            ArrayNode operationTags = objectMapper.createArrayNode();
            operationTags.add(source.displayName());
            operation.set("tags", operationTags);
            JsonNode operationId = operation.get("operationId");
            if (operationId != null && operationId.isTextual()) {
                operation.put("operationId", source.id() + "_" + operationId.asText());
            }
        });
    }

    private void mergeComponents(ObjectNode targetComponents, JsonNode sourceComponents) {
        if (!sourceComponents.isObject()) {
            return;
        }
        sourceComponents.fields().forEachRemaining(categoryEntry -> {
            ObjectNode targetCategory = objectChild(targetComponents, categoryEntry.getKey());
            JsonNode sourceCategory = categoryEntry.getValue();
            if (sourceCategory.isObject()) {
                sourceCategory.fields().forEachRemaining(componentEntry ->
                        targetCategory.set(componentEntry.getKey(), componentEntry.getValue().deepCopy()));
            }
        });
    }

    private ObjectNode objectChild(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode child = objectMapper.createObjectNode();
        parent.set(fieldName, child);
        return child;
    }

    private String trimTrailingSlash(String uri) {
        String trimmed = uri == null ? "" : uri.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record SwaggerConfig(
            String configUrl,
            String oauth2RedirectUrl,
            String url,
            String validatorUrl,
            List<OpenApiUrl> urls) {}

    public record OpenApiUrl(String name, String url) {}

    private record OpenApiSource(String id, String displayName, String serviceUri) {}

    private record OpenApiDocument(OpenApiSource source, ObjectNode document, String errorMessage) {
        private static OpenApiDocument succeeded(OpenApiSource source, ObjectNode document) {
            return new OpenApiDocument(source, document, null);
        }

        private static OpenApiDocument failed(OpenApiSource source, String errorMessage) {
            return new OpenApiDocument(source, null, errorMessage);
        }

        private boolean failed() {
            return document == null;
        }
    }
}
