package com.aicampus.common.dto;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeDocument(
        String documentId,
        String title,
        String content,
        String category,
        String source,
        List<String> tags,
        List<String> roles,
        String createdBy,
        LocalDateTime createdAt
) {
}
