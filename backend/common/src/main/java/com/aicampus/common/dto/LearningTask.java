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
        List<LearningEvidence> evidence,
        String taskDate,
        int estimatedMinutes,
        List<String> dependencies,
        String source,
        Integer actualMinutes,
        boolean delayed,
        String deferredUntil) {
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
                List.of(),
                null,
                Math.max(0, estimatedHours) * 60,
                List.of(),
                "LEGACY",
                null,
                false,
                null);
    }

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
            Instant updatedAt,
            List<String> prerequisites,
            List<LearningReference> references,
            String referenceStatus,
            List<LearningEvidence> evidence) {
        this(taskId, week, title, description, skillGap, stage, acceptanceCriteria,
                practiceDeliverable, estimatedHours, status, feedback, completedAt,
                updatedAt, prerequisites, references, referenceStatus, evidence, null,
                Math.max(0, estimatedHours) * 60, prerequisites, "AI_PLAN", null, false, null);
    }

    public String scheduledDate() { return taskDate; }
    public List<String> prerequisitesOrDependencies() { return dependencies.isEmpty() ? prerequisites : dependencies; }
}
