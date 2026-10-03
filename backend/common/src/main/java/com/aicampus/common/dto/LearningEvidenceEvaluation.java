package com.aicampus.common.dto;

import java.util.List;

public record LearningEvidenceEvaluation(
        int score,
        String conclusion,
        List<String> strengths,
        List<String> gaps,
        List<String> suggestions,
        List<InterviewEvidenceNote> evidence,
        boolean mocked) {}
