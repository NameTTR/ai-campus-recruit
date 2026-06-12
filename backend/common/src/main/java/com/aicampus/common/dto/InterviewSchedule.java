package com.aicampus.common.dto;

import com.aicampus.common.enums.InterviewScheduleStatus;
import java.time.LocalDateTime;

public record InterviewSchedule(
        String scheduleId,
        String deliveryId,
        String companyId,
        String studentId,
        String jobId,
        String title,
        LocalDateTime startTime,
        int durationMinutes,
        String location,
        String meetingUrl,
        String note,
        InterviewScheduleStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public InterviewSchedule {
        if (durationMinutes <= 0) {
            durationMinutes = 30;
        }
        if (status == null) {
            status = InterviewScheduleStatus.PROPOSED;
        }
    }
}
