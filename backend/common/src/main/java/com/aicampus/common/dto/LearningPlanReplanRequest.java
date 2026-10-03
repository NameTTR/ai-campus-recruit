package com.aicampus.common.dto;

public record LearningPlanReplanRequest(
        String reason,
        Integer weeklyHours,
        Integer durationWeeks,
        String interviewSessionId,
        Boolean previewOnly) {
    public LearningPlanReplanRequest(
            String reason, Integer weeklyHours, Integer durationWeeks, String interviewSessionId) {
        this(reason, weeklyHours, durationWeeks, interviewSessionId, false);
    }
}
