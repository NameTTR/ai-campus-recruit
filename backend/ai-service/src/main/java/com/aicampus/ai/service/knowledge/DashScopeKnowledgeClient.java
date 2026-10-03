package com.aicampus.ai.service.knowledge;

import com.aicampus.ai.service.DashScopeClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DashScopeKnowledgeClient {
    private final DashScopeClient client;
    private final KnowledgeBaseProperties.Semantic properties;
    public DashScopeKnowledgeClient(DashScopeClient client, KnowledgeBaseProperties properties) {
        this.client = client;
        this.properties = properties.getSemantic();
    }
    public boolean isConfigured() { return properties.isEnabled() && client.isConfigured(); }
    public boolean isRerankEnabled() { return isConfigured() && properties.isRerankEnabled(); }
    public String model() { return properties.getEmbeddingModel(); }
    public String version() { return properties.getVersion(); }
    public int dimension() { return properties.getDimension(); }

    public List<List<Double>> embed(List<String> texts, boolean query) {
        if (!isConfigured()) throw new IllegalStateException("Semantic embeddings are unavailable");
        if (texts == null || texts.isEmpty() || texts.size() > 10)
            throw new IllegalArgumentException("Embedding batches must contain 1 to 10 texts");
        return client.postJson(properties.getEmbeddingUrl(), Map.of("model", model(),
                "input", Map.of("texts", texts), "parameters", Map.of("dimension", dimension(),
                        "text_type", query ? "query" : "document", "output_type", "dense")), response -> {
            Object output = response == null ? null : response.get("output");
            Object entries = output instanceof Map<?, ?> m ? m.get("embeddings") : null;
            if (!(entries instanceof List<?> values) || values.size() != texts.size())
                throw new IllegalStateException("DashScope embedding count is invalid");
            List<List<Double>> vectors = new ArrayList<>(java.util.Collections.nCopies(texts.size(), null));
            for (Object entry : values) {
                if (!(entry instanceof Map<?, ?> item) || !(item.get("embedding") instanceof List<?> raw)
                        || !(item.get("text_index") instanceof Number indexNumber))
                    throw new IllegalStateException("DashScope embedding entry is invalid");
                int index = indexNumber.intValue();
                if (index < 0 || index >= texts.size() || vectors.get(index) != null || raw.size() != dimension())
                    throw new IllegalStateException("DashScope embedding index or dimension is invalid");
                List<Double> vector = new ArrayList<>();
                double norm = 0;
                for (Object value : raw) {
                    if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()))
                        throw new IllegalStateException("DashScope embedding value is invalid");
                    vector.add(n.doubleValue());
                    norm += n.doubleValue() * n.doubleValue();
                }
                if (norm <= 0) throw new IllegalStateException("DashScope embedding is empty");
                vectors.set(index, List.copyOf(vector));
            }
            if (vectors.contains(null)) throw new IllegalStateException("DashScope embedding index is missing");
            return List.copyOf(vectors);
        });
    }

    public List<RerankResult> rerank(String query, List<String> texts) {
        if (!isRerankEnabled()) throw new IllegalStateException("Semantic reranking is unavailable");
        boolean nativeGte = properties.getRerankModel().startsWith("gte-");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getRerankModel());
        if (nativeGte) {
            payload.put("input", Map.of("query", query, "documents", texts));
            payload.put("parameters", Map.of("top_n", texts.size(), "return_documents", false));
        } else {
            payload.put("query", query);
            payload.put("documents", texts);
            payload.put("top_n", texts.size());
            payload.put("return_documents", false);
        }
        return client.postJson(properties.getRerankUrl(), payload, response -> {
            Map<?, ?> body = nativeGte && response != null && response.get("output") instanceof Map<?, ?> m ? m : response;
            Object result = body == null ? null : body.get("results");
            if (!(result instanceof List<?> values) || values.size() != texts.size())
                throw new IllegalStateException("DashScope rerank results are incomplete");
            List<RerankResult> ranks = new ArrayList<>();
            java.util.Set<Integer> indices = new java.util.HashSet<>();
            for (Object entry : values) {
                if (!(entry instanceof Map<?, ?> item) || !(item.get("index") instanceof Number index)
                        || !(item.get("relevance_score") instanceof Number score)
                        || index.intValue() < 0 || index.intValue() >= texts.size()
                        || !indices.add(index.intValue()) || !Double.isFinite(score.doubleValue()))
                    throw new IllegalStateException("DashScope rerank entry is invalid");
                ranks.add(new RerankResult(index.intValue(), score.doubleValue()));
            }
            return ranks.stream().sorted(java.util.Comparator.comparingDouble(RerankResult::score).reversed()).toList();
        });
    }
    public record RerankResult(int index, double score) {}
}
