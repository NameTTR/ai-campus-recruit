package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record KnowledgeAnswerResponse(String query, String answer, List<KnowledgeCitation> citations,
        boolean mocked, String provider, Instant generatedAt, String retrievalMode, String generationMode,
        String evidenceStatus, String algorithmVersion, String permissionVersion, List<KnowledgeAnswerClaim> claims,
        String inputFingerprint, AnalysisMetadata metadata) {
    public KnowledgeAnswerResponse(String query, String answer, List<KnowledgeCitation> citations,
            boolean mocked, String provider, Instant generatedAt, String retrievalMode, String generationMode,
            String evidenceStatus, String algorithmVersion, String permissionVersion, List<KnowledgeAnswerClaim> claims,
            String inputFingerprint) {
        this(query, answer, citations, mocked, provider, generatedAt, retrievalMode, generationMode, evidenceStatus,
                algorithmVersion, permissionVersion, claims, inputFingerprint, null);
    }
    public KnowledgeAnswerResponse(String query, String answer, List<KnowledgeCitation> citations,
            boolean mocked, String provider, Instant generatedAt) {
        this(query, answer, citations, mocked, provider, generatedAt, "LEGACY", mocked ? "RETRIEVAL_ONLY" : "AI",
                citations == null || citations.isEmpty() ? "NO_EVIDENCE" : "RETRIEVED", "legacy", null, List.of(), null);
    }
}
