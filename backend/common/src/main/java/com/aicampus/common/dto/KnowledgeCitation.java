package com.aicampus.common.dto;

public record KnowledgeCitation(
        String documentId,
        String chunkId,
        String title,
        String source,
        int score,
        String snippet) {
}
