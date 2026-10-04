package com.aicampus.common.dto;

import java.util.List;

import java.util.List;

public record LearningPlanReplanRequest(
        String reason,
        Integer weeklyHours,
        Integer durationWeeks,
        String interviewSessionId,
        Boolean previewOnly,
        String startDate,
        List<String> studyDays,
        Integer dailyMinutesCap) {
    public LearningPlanReplanRequest(
            String reason, Integer weeklyHours, Integer durationWeeks,
            String interviewSessionId, Boolean previewOnly) {
        this(reason, weeklyHours, durationWeeks, interviewSessionId, previewOnly,
                null, List.of(), null);
    }

    public LearningPlanReplanRequest(
            String reason, Integer weeklyHours, Integer durationWeeks, String interviewSessionId) {
        this(reason, weeklyHours, durationWeeks, interviewSessionId, false, null, List.of(), null);
    }
}
