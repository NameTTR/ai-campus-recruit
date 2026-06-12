package com.aicampus.common.dto;

import java.time.Instant;
import java.util.Map;

public record KnowledgeBaseStats(
        int documentCount,
        int chunkCount,
        Map<String, Long> categoryCounts,
        Map<String, Long> roleCounts,
        Map<String, Long> sourceCounts,
        Map<String, Long> tagCounts,
        String corpusVersion,
        boolean seedEnabled,
        boolean persistentStore,
        Instant generatedAt
) {
}
