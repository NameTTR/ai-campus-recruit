package com.aicampus.common.dto;

import java.util.List;

import java.util.List;

public record LearningPlanCreateRequest(
        String studentId,
        String resumeId,
        String jobId,
        String matchId,
        String targetRole,
        Integer weeklyHours,
        Integer durationWeeks,
        String startDate,
        List<String> studyDays,
        Integer dailyMinutesCap) {
    public LearningPlanCreateRequest(
            String studentId, String resumeId, String jobId, String matchId,
            String targetRole, Integer weeklyHours, Integer durationWeeks) {
        this(studentId, resumeId, jobId, matchId, targetRole, weeklyHours, durationWeeks,
                null, List.of(), null);
    }
}
