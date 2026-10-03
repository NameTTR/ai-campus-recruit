package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record InterviewQuestionFeedback(
        String questionId,
        int score,
        List<String> strengths,
        List<String> gaps,
        List<String> suggestions,
        String summary,
        boolean mocked,
        List<InterviewDimensionScore> dimensions,
        List<InterviewEvidenceNote> evidence,
        String rubricVersion,
        String followUpQuestion,
        AnalysisMetadata analysisMetadata,
        Instant evaluatedAt) {
    public InterviewQuestionFeedback(
            String questionId,
            int score,
            List<String> strengths,
            List<String> gaps,
            List<String> suggestions,
            String summary,
            boolean mocked) {
        this(
                questionId,
                score,
                strengths,
                gaps,
                suggestions,
                summary,
                mocked,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null);
    }
}
