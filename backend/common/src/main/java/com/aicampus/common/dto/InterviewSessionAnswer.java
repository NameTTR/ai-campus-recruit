package com.aicampus.common.dto;

import java.time.Instant;

public record InterviewSessionAnswer(
        String questionId,
        String answer,
        Instant answeredAt,
        String evaluationStatus,
        InterviewQuestionFeedback evaluation,
        String evaluationError) {
    public InterviewSessionAnswer {
        evaluationStatus = evaluationStatus == null ? "PENDING" : evaluationStatus;
    }

    public InterviewSessionAnswer(String questionId, String answer, Instant answeredAt) {
        this(questionId, answer, answeredAt, "PENDING", null, null);
    }
}
