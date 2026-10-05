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
        String difficultyNote,
        String reportType,
        String completionScope,
        List<String> unansweredQuestionIds,
        List<InterviewAnswerAttempt> selectedAttempts,
        List<InterviewAttemptComparison> attemptComparisons,
        List<InterviewNextAction> nextActions) {
    public InterviewSessionReport {
        reportType = reportType == null ? "FINAL" : reportType;
        unansweredQuestionIds = unansweredQuestionIds == null ? List.of() : unansweredQuestionIds;
        selectedAttempts = selectedAttempts == null ? List.of() : selectedAttempts;
        attemptComparisons = attemptComparisons == null ? List.of() : attemptComparisons;
        nextActions = nextActions == null ? List.of() : nextActions;
    }

    public InterviewSessionReport(String sessionId, int overallScore, List<String> strengths,
            List<String> gaps, List<String> recommendations, List<InterviewQuestionFeedback> questionFeedback,
            Instant generatedAt, boolean mocked, String rubricVersion, String comparisonNote,
            List<String> comparableSessionIds, String difficultyNote) {
        this(sessionId, overallScore, strengths, gaps, recommendations, questionFeedback,
                generatedAt, mocked, rubricVersion, comparisonNote, comparableSessionIds,
                difficultyNote, "FINAL", null, List.of(), List.of(), List.of(), List.of());
    }
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
