package com.aicampus.common.dto;

import java.util.List;

public record AiCoachAdviceResponse(
        String studentId,
        String targetRole,
        int readinessScore,
        String headline,
        List<String> priorityActions,
        List<String> riskWarnings,
        List<String> learningPath,
        List<String> interviewDrills,
        List<String> searchKeywords,
        boolean mocked) {
}
