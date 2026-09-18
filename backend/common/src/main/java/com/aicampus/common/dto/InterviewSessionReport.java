package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record InterviewSessionReport(
        String sessionId,
        int overallScore,
        List<String> strengths,
        List<String> gaps,
        List<String> recommendations,
        List<InterviewQuestionFeedback> questionFeedback,
        Instant generatedAt,
        boolean mocked) {
}
