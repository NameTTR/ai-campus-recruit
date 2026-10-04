package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record LearningEvidence(
        String evidenceId,
        String planId,
        String taskId,
        String studentId,
        String description,
        List<String> links,
        String status,
        LearningEvidenceEvaluation evaluation,
        String error,
        AnalysisMetadata analysisMetadata,
        Instant submittedAt,
        Instant evaluatedAt,
        boolean confirmed,
        boolean resumeCandidate) {
    public LearningEvidence(
            String evidenceId, String planId, String taskId, String studentId,
            String description, List<String> links, String status,
            LearningEvidenceEvaluation evaluation, String error,
            AnalysisMetadata analysisMetadata, Instant submittedAt, Instant evaluatedAt) {
        this(evidenceId, planId, taskId, studentId, description, links, status, evaluation,
                error, analysisMetadata, submittedAt, evaluatedAt, false, false);
    }
}
