package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record AiSearchResponse(String query, List<AiSearchResult> results, Instant generatedAt,
        String retrievalMode, String algorithmVersion, String evidenceStatus, String permissionVersion, AnalysisMetadata metadata) {
    public AiSearchResponse(String query, List<AiSearchResult> results, Instant generatedAt, String retrievalMode,
            String algorithmVersion, String evidenceStatus, String permissionVersion) {
        this(query, results, generatedAt, retrievalMode, algorithmVersion, evidenceStatus, permissionVersion, null);
    }
    public AiSearchResponse(String query, List<AiSearchResult> results, Instant generatedAt) {
        this(query, results, generatedAt, "LEGACY", "legacy", results == null || results.isEmpty() ? "NO_EVIDENCE" : "RETRIEVED", null);
    }
}
