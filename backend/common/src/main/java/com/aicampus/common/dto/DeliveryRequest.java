package com.aicampus.common.dto;

public record DeliveryRequest(
        String studentId,
        String resumeId,
        String jobId,
        String resumeSourceFormat,
        String resumeParseStatus,
        int resumeParsedTextLength
) {
    public DeliveryRequest {
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

    public DeliveryRequest(String studentId, String resumeId, String jobId) {
        this(studentId, resumeId, jobId, "UNKNOWN", "UNKNOWN", 0);
    }
}
