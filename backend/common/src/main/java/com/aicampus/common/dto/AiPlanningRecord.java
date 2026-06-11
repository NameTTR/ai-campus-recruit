package com.aicampus.common.dto;

import java.time.Instant;

public record AiPlanningRecord(
        String recordId,
        String studentId,
        String operation,
        String resumeId,
        String targetRole,
        ResumeRewriteResponse resumeRewrite,
        CareerPlanResponse careerPlan,
        boolean mocked,
        Instant createdAt) {
}
