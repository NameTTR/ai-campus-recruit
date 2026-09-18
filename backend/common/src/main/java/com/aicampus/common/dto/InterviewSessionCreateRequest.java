package com.aicampus.common.dto;

public record InterviewSessionCreateRequest(
        String studentId,
        String resumeId,
        String jobId,
        String matchId,
        String targetRole,
        Integer questionCount) {
}
