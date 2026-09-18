package com.aicampus.common.dto;

public record LearningPlanReplanRequest(
        String reason,
        Integer weeklyHours,
        Integer durationWeeks,
        String interviewSessionId) {
}
