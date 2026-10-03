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
        Instant updatedAt,
        String revisionReason,
        AnalysisMetadata analysisMetadata) {
    public LearningPlan(
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
        this(
                planId,
                rootPlanId,
                studentId,
                resumeId,
                jobId,
                matchId,
                targetRole,
                contextSnapshot,
                weeklyHours,
                durationWeeks,
                status,
                version,
                revisionOfPlanId,
                tasks,
                mocked,
                createdAt,
                updatedAt,
                null,
                null);
    }
}
