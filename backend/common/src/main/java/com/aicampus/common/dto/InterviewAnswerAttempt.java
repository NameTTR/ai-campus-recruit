package com.aicampus.common.dto;

import java.time.Instant;

public record InterviewAnswerAttempt(String attemptId, String questionId, int attemptNo,
        String answer, Instant submittedAt, String evaluationStatus,
        InterviewQuestionFeedback evaluation, String evaluationError, String inputFingerprint,
        boolean selectedForReport, Instant selectedAt, String selectionReason) {}
