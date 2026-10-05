package com.aicampus.common.dto;

public record InterviewSessionCreateRequest(
        String studentId,
        String resumeId,
        String jobId,
        String matchId,
        String targetRole,
        Integer questionCount,
        String mode,
        String sourceType,
        String sourceId,
        Integer timerMinutes) {
    public InterviewSessionCreateRequest(String studentId, String resumeId, String jobId,
            String matchId, String targetRole, Integer questionCount) {
        this(studentId, resumeId, jobId, matchId, targetRole, questionCount, null, null, null, null);
    }
}
