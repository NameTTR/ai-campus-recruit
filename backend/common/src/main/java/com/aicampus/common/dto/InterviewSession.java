package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record InterviewSession(
        String sessionId,
        String studentId,
        String resumeId,
        String jobId,
        String matchId,
        String targetRole,
        RecruitmentContextSnapshot contextSnapshot,
        String status,
        List<InterviewSessionQuestion> questions,
        List<InterviewSessionAnswer> answers,
        InterviewSessionReport report,
        boolean mocked,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
