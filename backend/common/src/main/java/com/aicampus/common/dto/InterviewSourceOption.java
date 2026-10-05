package com.aicampus.common.dto;

public record InterviewSourceOption(String sourceType, String sourceId, String label,
        String description, String resumeId, String jobId, String matchId, String targetRole,
        String sourceKind) {}
