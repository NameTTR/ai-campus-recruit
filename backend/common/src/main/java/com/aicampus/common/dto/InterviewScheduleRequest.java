package com.aicampus.common.dto;

import java.time.LocalDateTime;

public record InterviewScheduleRequest(
        String deliveryId,
        String companyId,
        String studentId,
        String jobId,
        String title,
        LocalDateTime startTime,
        Integer durationMinutes,
        String location,
        String meetingUrl,
        String note
) {
}
