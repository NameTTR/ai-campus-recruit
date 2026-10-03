package com.aicampus.common.dto;

import java.util.List;

public record AiSearchResult(String id, String type, String title, String owner, String summary,
        int score, List<String> highlights, KnowledgeCitation citation) {
    public AiSearchResult(String id, String type, String title, String owner, String summary, int score, List<String> highlights) {
        this(id, type, title, owner, summary, score, highlights, null);
    }
}
