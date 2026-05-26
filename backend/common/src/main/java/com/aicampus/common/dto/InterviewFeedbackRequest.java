package com.aicampus.common.dto;

public record InterviewFeedbackRequest(
        String studentId,
        String questionId,
        String question,
        String answer,
        String targetRole) {
}
