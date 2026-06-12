package com.aicampus.ai.service.knowledge;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeChunkRecord(
        String chunkId,
        String documentId,
        int chunkIndex,
        String title,
        String text,
        String category,
        String source,
        List<String> tags,
        List<String> roles,
        String createdBy,
        LocalDateTime createdAt,
        List<Double> embedding) {
}
