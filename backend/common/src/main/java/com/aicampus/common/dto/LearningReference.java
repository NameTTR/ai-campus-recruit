package com.aicampus.common.dto;

public record LearningReference(String documentId, String title, String source, String snippet, Integer documentVersion) {
    public LearningReference(String documentId, String title, String source, String snippet) {
        this(documentId, title, source, snippet, null);
    }
}
