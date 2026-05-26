package com.aicampus.common.dto;

import java.util.List;

public record MatchResult(
        String matchId,
        String resumeId,
        String jobId,
        String studentId,
        int score,
        List<String> strengths,
        List<String> gaps,
        List<String> suggestions
) {
}

