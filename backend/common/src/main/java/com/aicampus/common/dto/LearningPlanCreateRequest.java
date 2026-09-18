package com.aicampus.common.dto;

public record LearningPlanCreateRequest(
        String studentId,
        String resumeId,
        String jobId,
        String matchId,
        String targetRole,
        Integer weeklyHours,
        Integer durationWeeks) {
}
