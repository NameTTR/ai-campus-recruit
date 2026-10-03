package com.aicampus.common.dto;

import java.util.List;

public record KnowledgeCitation(String documentId, String chunkId, String title, String source,
        int score, String snippet, Integer chunkIndex, Integer startOffset, Integer endOffset,
        String heading, List<String> roles) {
    public KnowledgeCitation(String documentId, String chunkId, String title, String source, int score, String snippet) {
        this(documentId, chunkId, title, source, score, snippet, null, null, null, null, List.of());
    }
}
