package com.aicampus.common.dto;

import java.util.List;

public record InterviewQuestionRequest(
        String studentId,
        String resumeId,
        String jobId,
        String targetRole,
        List<String> skills) {
}
