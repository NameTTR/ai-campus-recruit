package com.aicampus.common.dto;

import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import java.time.Instant;

public record CandidateScreenTask(
        String taskId,
        String deliveryId,
        String companyId,
        String studentId,
        String resumeId,
        String jobId,
        CandidateScreenTaskStatus status,
        CandidateScreenTaskSource source,
        String message,
        CandidateScreenResult result,
        Instant createdAt,
        Instant updatedAt) {
    public CandidateScreenTask {
        taskId = valueOr(taskId, "AST-UNKNOWN");
        deliveryId = valueOr(deliveryId, "D001");
        companyId = valueOr(companyId, "C001");
        studentId = valueOr(studentId, "S001");
        resumeId = valueOr(resumeId, "R001");
        jobId = valueOr(jobId, "J001");
        status = status == null ? CandidateScreenTaskStatus.PENDING : status;
        source = source == null ? CandidateScreenTaskSource.RUNTIME : source;
        message = valueOr(message, "");
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
