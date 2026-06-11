package com.aicampus.common.dto;

import java.util.List;

public record CareerPlanRequest(
        String studentId,
        String targetRole,
        List<String> skills,
        List<String> interests,
        String resumeSummary,
        Integer timeframeWeeks) {
}
