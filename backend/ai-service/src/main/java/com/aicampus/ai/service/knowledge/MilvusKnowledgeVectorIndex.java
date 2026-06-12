package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeVectorStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class MilvusKnowledgeVectorIndex implements KnowledgeVectorIndex {
    private static final Logger log = LoggerFactory.getLogger(MilvusKnowledgeVectorIndex.class);

    private final KnowledgeBaseProperties.Vector properties;
    private final RestClient restClient;
    private final AtomicBoolean collectionChecked = new AtomicBoolean(false);
    private final Map<String, Boolean> indexedChunks = new ConcurrentHashMap<>();
    private volatile boolean available;
    private volatile String fallbackReason = "Milvus collection has not been checked yet";

    public MilvusKnowledgeVectorIndex(KnowledgeBaseProperties.Vector properties) {
        this.properties = properties;
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getEndpoint())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (hasText(properties.getToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken().trim());
        }
        this.restClient = builder.build();
    }

    @Override
    public void index(List<KnowledgeChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        if (!ensureCollection()) {
            return;
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (KnowledgeChunkRecord chunk : chunks) {
            if (chunk.embedding() == null || chunk.embedding().isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunk_id", chunk.chunkId());
            row.put(properties.getVectorField(), chunk.embedding());
            row.put("document_id", chunk.documentId());
            row.put("title", truncate(chunk.title(), 240));
            row.put("category", truncate(chunk.category(), 120));
            row.put("source", truncate(chunk.source(), 240));
            row.put("roles", String.join(",", chunk.roles() == null ? List.of() : chunk.roles()));
            data.add(row);
        }
        if (data.isEmpty()) {
            return;
        }

        try {
            JsonNode response = post("/v2/vectordb/entities/upsert", Map.of(
                    "collectionName", properties.getCollection(),
                    "data", data));
            if (success(response)) {
                data.stream()
                        .map(row -> String.valueOf(row.get("chunk_id")))
                        .forEach(chunkId -> indexedChunks.put(chunkId, true));
                available = true;
                fallbackReason = null;
                return;
            }
            markUnavailable("Milvus upsert failed: " + responseSummary(response));
        } catch (RuntimeException ex) {
            markUnavailable("Milvus upsert failed: " + safeMessage(ex));
        }
    }

    @Override
    public List<KnowledgeVectorMatch> search(List<Double> queryEmbedding, String role, int limit) {
        if (queryEmbedding == null || queryEmbedding.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (!ensureCollection()) {
            return List.of();
        }
        try {
            JsonNode response = post("/v2/vectordb/entities/search", Map.of(
                    "collectionName", properties.getCollection(),
                    "data", List.of(queryEmbedding),
                    "annsField", properties.getVectorField(),
                    "limit", Math.max(1, Math.min(100, limit)),
                    "outputFields", List.of("chunk_id", "roles")));
            if (!success(response)) {
                markUnavailable("Milvus search failed: " + responseSummary(response));
                return List.of();
            }
            available = true;
            fallbackReason = null;
            return parseMatches(response, role);
        } catch (RuntimeException ex) {
            markUnavailable("Milvus search failed: " + safeMessage(ex));
            return List.of();
        }
    }

    @Override
    public KnowledgeVectorStatus status() {
        ensureCollection();
        return new KnowledgeVectorStatus(
                "milvus-rest",
                true,
                available,
                properties.getEndpoint(),
                properties.getCollection(),
                properties.getDimension(),
                indexedChunks.size(),
                fallbackReason,
                Instant.now());
    }

    private boolean ensureCollection() {
        if (collectionChecked.get() && available) {
            return true;
        }
        try {
            JsonNode hasResponse = post("/v2/vectordb/collections/has", Map.of(
                    "collectionName", properties.getCollection()));
            if (!success(hasResponse)) {
                markUnavailable("Milvus collection check failed: " + responseSummary(hasResponse));
                collectionChecked.set(true);
                return false;
            }
            boolean exists = hasResponse.path("data").asBoolean(false);
            if (!exists && !createCollection()) {
                collectionChecked.set(true);
                return false;
            }
            loadCollection();
            available = true;
            fallbackReason = null;
            collectionChecked.set(true);
            return true;
        } catch (RuntimeException ex) {
            markUnavailable("Milvus is unavailable: " + safeMessage(ex));
            collectionChecked.set(true);
            return false;
        }
    }

    private boolean createCollection() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", properties.getCollection());
        body.put("dimension", properties.getDimension());
        body.put("metricType", "COSINE");
        body.put("idType", "VarChar");
        body.put("primaryFieldName", "chunk_id");
        body.put("vectorFieldName", properties.getVectorField());
        body.put("autoID", false);
        body.put("params", Map.of("max_length", "128"));
        JsonNode response = post("/v2/vectordb/collections/create", body);
        if (success(response)) {
            return true;
        }
        markUnavailable("Milvus collection creation failed: " + responseSummary(response));
        return false;
    }

    private void loadCollection() {
        try {
            JsonNode response = post("/v2/vectordb/collections/load", Map.of(
                    "collectionName", properties.getCollection()));
            if (!success(response)) {
                log.warn("Milvus collection load returned non-success response: {}", responseSummary(response));
            }
        } catch (RuntimeException ex) {
            log.warn("Milvus collection load failed; search may still work if collection is already loaded", ex);
        }
    }

    private JsonNode post(String path, Object body) {
        return restClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private List<KnowledgeVectorMatch> parseMatches(JsonNode response, String role) {
        JsonNode data = response.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<KnowledgeVectorMatch> matches = new ArrayList<>();
        for (JsonNode item : data) {
            String chunkId = firstText(item, "chunk_id", "id");
            String roles = item.path("roles").asText("");
            if (!hasText(chunkId) || !canRead(roles, role)) {
                continue;
            }
            double distance = item.has("distance") ? item.path("distance").asDouble() : item.path("score").asDouble(0);
            int score = (int) Math.round(Math.max(0, Math.min(1, distance)) * 100);
            matches.add(new KnowledgeVectorMatch(chunkId, score));
        }
        return matches;
    }

    private boolean canRead(String roles, String role) {
        if (!hasText(role)) {
            return true;
        }
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        return List.of(roles.split(",")).stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .anyMatch(value -> "ALL".equals(value) || normalizedRole.equals(value));
    }

    private void markUnavailable(String reason) {
        available = false;
        fallbackReason = reason;
        log.warn(reason);
    }

    private static boolean success(JsonNode response) {
        if (response == null) {
            return false;
        }
        JsonNode code = response.path("code");
        if (code.isInt() || code.isLong()) {
            return code.asInt() == 0 || code.asInt() == 200;
        }
        String text = code.asText("");
        return text.isBlank() || "0".equals(text) || "200".equals(text) || "Success".equalsIgnoreCase(text);
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.asText("").isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private static String responseSummary(JsonNode response) {
        if (response == null) {
            return "empty response";
        }
        String message = response.path("message").asText("");
        if (!message.isBlank()) {
            return message;
        }
        return truncate(response.toString(), 300);
    }

    private static String safeMessage(RuntimeException ex) {
        if (ex instanceof RestClientException && ex.getMessage() != null) {
            return ex.getMessage();
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
