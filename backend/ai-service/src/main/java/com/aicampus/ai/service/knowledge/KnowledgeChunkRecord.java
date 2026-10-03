package com.aicampus.ai.service.knowledge;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeChunkRecord(String chunkId, String documentId, int chunkIndex, String title,
        String text, String category, String source, List<String> tags, List<String> roles, String createdBy,
        LocalDateTime createdAt, List<Double> embedding, String embeddingModel, Integer embeddingDimension,
        String indexVersion, Integer startOffset, Integer endOffset, String heading) {
    public KnowledgeChunkRecord(String chunkId, String documentId, int chunkIndex, String title,
            String text, String category, String source, List<String> tags, List<String> roles, String createdBy,
            LocalDateTime createdAt, List<Double> embedding) {
        this(chunkId, documentId, chunkIndex, title, text, category, source, tags, roles, createdBy, createdAt,
                embedding, null, null, "legacy", null, null, null);
    }
    public KnowledgeChunkRecord withRoles(List<String> updatedRoles) {
        return new KnowledgeChunkRecord(chunkId, documentId, chunkIndex, title, text, category, source, tags,
                updatedRoles, createdBy, createdAt, embedding, embeddingModel, embeddingDimension, indexVersion,
                startOffset, endOffset, heading);
    }
}
