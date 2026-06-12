package com.aicampus.common.dto;

import java.util.List;

public record KnowledgeDocumentRequest(
        String title,
        String content,
        String category,
        String source,
        List<String> tags,
        List<String> roles
) {
}
