package com.aicampus.common.dto;

import java.time.Instant;

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
        Instant updatedAt) {
}
