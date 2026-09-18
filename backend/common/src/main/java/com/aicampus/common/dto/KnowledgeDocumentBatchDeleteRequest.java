package com.aicampus.common.dto;

import java.util.List;

public record KnowledgeDocumentBatchDeleteRequest(List<String> documentIds) {
}
