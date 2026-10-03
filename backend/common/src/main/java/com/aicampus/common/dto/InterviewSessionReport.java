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
        boolean mocked,
        String rubricVersion,
        String comparisonNote,
        List<String> comparableSessionIds,
        String difficultyNote) {
    public InterviewSessionReport(
            String sessionId,
            int overallScore,
            List<String> strengths,
            List<String> gaps,
            List<String> recommendations,
            List<InterviewQuestionFeedback> questionFeedback,
            Instant generatedAt,
            boolean mocked) {
        this(
                sessionId,
                overallScore,
                strengths,
                gaps,
                recommendations,
                questionFeedback,
                generatedAt,
                mocked,
                null,
                null,
                List.of(),
                null);
    }
}
