package com.aicampus.common.dto;

import com.aicampus.common.enums.DeliveryStatus;
import java.time.LocalDateTime;

public record DeliveryRecord(
        String deliveryId,
        String studentId,
        String resumeId,
        String jobId,
        String companyId,
        String resumeSourceFormat,
        String resumeParseStatus,
        int resumeParsedTextLength,
        DeliveryStatus status,
        LocalDateTime createdAt
) {
    public DeliveryRecord {
        if (resumeSourceFormat == null || resumeSourceFormat.isBlank()) {
            resumeSourceFormat = "UNKNOWN";
        }
        if (resumeParseStatus == null || resumeParseStatus.isBlank()) {
            resumeParseStatus = "UNKNOWN";
        }
        if (resumeParsedTextLength < 0) {
            resumeParsedTextLength = 0;
        }
    }

    public DeliveryRecord(
            String deliveryId,
            String studentId,
            String resumeId,
            String jobId,
            String companyId,
            DeliveryStatus status,
            LocalDateTime createdAt
    ) {
        this(deliveryId, studentId, resumeId, jobId, companyId, "UNKNOWN", "UNKNOWN", 0, status, createdAt);
    }
}
