package com.aicampus.common.dto;
import java.time.Instant;
public record AnalysisMetadata(String inputFingerprint, String algorithmVersion, String model,
                               String promptVersion, String source, Instant generatedAt,
                               EvidenceContext evidenceContext) {
    /** Keeps the six-field constructor used by all legacy services. */
    public AnalysisMetadata(String inputFingerprint, String algorithmVersion, String model,
                            String promptVersion, String source, Instant generatedAt) {
        this(inputFingerprint, algorithmVersion, model, promptVersion, source, generatedAt, null);
    }

    public AnalysisMetadata withEvidenceContext(EvidenceContext value) {
        return new AnalysisMetadata(inputFingerprint, algorithmVersion, model, promptVersion,
                source, generatedAt, value);
    }
}
