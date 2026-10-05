package com.aicampus.common.dto;

public record InterviewFeedbackRequest(
        String studentId,
        String questionId,
        String question,
        String answer,
        String targetRole,
        String authorizedContext) {
    public InterviewFeedbackRequest(String studentId, String questionId, String question,
            String answer, String targetRole) {
        this(studentId, questionId, question, answer, targetRole, null);
    }
}
