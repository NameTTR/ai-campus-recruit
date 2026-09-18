package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record LearningPlan(
        String planId,
        String rootPlanId,
        String studentId,
        String resumeId,
        String jobId,
        String matchId,
        String targetRole,
        RecruitmentContextSnapshot contextSnapshot,
        int weeklyHours,
        int durationWeeks,
        String status,
        int version,
        String revisionOfPlanId,
        List<LearningTask> tasks,
        boolean mocked,
        Instant createdAt,
        Instant updatedAt) {
}
