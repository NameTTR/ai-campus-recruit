package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record KnowledgeAnswerResponse(
        String query,
        String answer,
        List<KnowledgeCitation> citations,
        boolean mocked,
        String provider,
        Instant generatedAt) {
}
