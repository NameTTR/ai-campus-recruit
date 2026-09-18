package com.aicampus.common.dto;

import java.util.List;

public record KnowledgeDocumentBatchDeleteResult(
        int requestedCount,
        int deletedCount,
        List<String> deletedDocumentIds,
        List<String> missingDocumentIds) {
}
