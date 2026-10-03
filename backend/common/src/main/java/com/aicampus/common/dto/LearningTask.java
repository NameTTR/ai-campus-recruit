package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record LearningTask(
        String taskId,
        int week,
        String title,
        String description,
        String skillGap,
        String stage,
        String acceptanceCriteria,
        String practiceDeliverable,
        int estimatedHours,
        String status,
        String feedback,
        Instant completedAt,
        Instant updatedAt,
        List<String> prerequisites,
        List<LearningReference> references,
        String referenceStatus,
        List<LearningEvidence> evidence) {
    public LearningTask(
            String taskId,
            int week,
            String title,
            String description,
            String skillGap,
            String stage,
            String acceptanceCriteria,
            String practiceDeliverable,
            int estimatedHours,
            String status,
            String feedback,
            Instant completedAt,
            Instant updatedAt) {
        this(
                taskId,
                week,
                title,
                description,
                skillGap,
                stage,
                acceptanceCriteria,
                practiceDeliverable,
                estimatedHours,
                status,
                feedback,
                completedAt,
                updatedAt,
                List.of(),
                List.of(),
                "LEGACY",
                List.of());
    }
}
