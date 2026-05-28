package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record InterviewRecord(
        String recordId,
        String studentId,
        String targetRole,
        String questionId,
        String question,
        String answer,
        int score,
        String summary,
        List<String> suggestions,
        boolean mocked,
        Instant createdAt) {
}
