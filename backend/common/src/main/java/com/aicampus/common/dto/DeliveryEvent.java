package com.aicampus.common.dto;

import com.aicampus.common.enums.DeliveryStatus;
import java.time.LocalDateTime;

public record DeliveryEvent(
        String eventId,
        String eventType,
        String deliveryId,
        String studentId,
        String resumeId,
        String jobId,
        String companyId,
        String resumeSourceFormat,
        String resumeParseStatus,
        int resumeParsedTextLength,
        DeliveryStatus deliveryStatus,
        String publishStatus,
        LocalDateTime createdAt
) {
    public DeliveryEvent {
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

    public DeliveryEvent(
            String eventId,
            String eventType,
            String deliveryId,
            String studentId,
            String resumeId,
            String jobId,
            String companyId,
            DeliveryStatus deliveryStatus,
            String publishStatus,
            LocalDateTime createdAt
    ) {
        this(eventId, eventType, deliveryId, studentId, resumeId, jobId, companyId,
                "UNKNOWN", "UNKNOWN", 0, deliveryStatus, publishStatus, createdAt);
    }
}
