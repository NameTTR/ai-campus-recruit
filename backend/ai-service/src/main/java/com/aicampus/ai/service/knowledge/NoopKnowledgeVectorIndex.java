package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeVectorStatus;
import java.time.Instant;
import java.util.List;

public class NoopKnowledgeVectorIndex implements KnowledgeVectorIndex {
    private final KnowledgeBaseProperties properties;

    public NoopKnowledgeVectorIndex(KnowledgeBaseProperties properties) {
        this.properties = properties;
    }

    @Override
    public void index(List<KnowledgeChunkRecord> chunks) {
    }

    @Override
    public List<KnowledgeVectorMatch> search(List<Double> queryEmbedding, String role, int limit) {
        return List.of();
    }

    @Override
    public KnowledgeVectorStatus status() {
        KnowledgeBaseProperties.Vector vector = properties.getVector();
        return new KnowledgeVectorStatus(
                vector.getProvider(),
                false,
                false,
                vector.getEndpoint(),
                vector.getCollection(),
                vector.getDimension(),
                0,
                "Milvus vector index is disabled; using local hash-vector retrieval",
                Instant.now());
    }
}
