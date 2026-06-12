package com.aicampus.common.dto;

import java.time.Instant;

public record KnowledgeVectorStatus(
        String provider,
        boolean enabled,
        boolean available,
        String endpoint,
        String collection,
        int dimension,
        long indexedChunkCount,
        String fallbackReason,
        Instant checkedAt) {
}
