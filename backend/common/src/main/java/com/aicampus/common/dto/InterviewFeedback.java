package com.aicampus.common.dto;

import java.util.List;

public record InterviewFeedback(
        int score,
        List<String> strengths,
        List<String> gaps,
        List<String> suggestions,
        String summary,
        boolean mocked,
        List<InterviewDimensionScore> dimensions,
        List<InterviewEvidenceNote> evidence,
        String followUpQuestion) {
    public InterviewFeedback(
            int score,
            List<String> strengths,
            List<String> gaps,
            List<String> suggestions,
            String summary,
            boolean mocked) {
        this(score, strengths, gaps, suggestions, summary, mocked, List.of(), List.of(), null);
    }
}
