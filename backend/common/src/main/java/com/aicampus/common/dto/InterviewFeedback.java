package com.aicampus.common.dto;

import java.util.List;

public record InterviewFeedback(
        int score,
        List<String> strengths,
        List<String> gaps,
        List<String> suggestions,
        String summary,
        boolean mocked) {
}
