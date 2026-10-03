package com.aicampus.common.dto;

public record InterviewEvaluationResponse(
        String sessionId,
        String questionId,
        String status,
        InterviewQuestionFeedback feedback,
        String error,
        InterviewSessionQuestion followUpQuestion) {}
