package com.aicampus.common.dto;

import java.util.List;

public record CareerPlanRequest(
        String studentId,
        String targetRole,
        List<String> skills,
        List<String> interests,
        String resumeSummary,
        Integer timeframeWeeks,
        Integer weeklyHours,
        List<String> requiredSkills) {
    public CareerPlanRequest(
            String studentId,
            String targetRole,
            List<String> skills,
            List<String> interests,
            String resumeSummary,
            Integer timeframeWeeks) {
        this(
                studentId,
                targetRole,
                skills,
                interests,
                resumeSummary,
                timeframeWeeks,
                null,
                List.of());
    }
}
