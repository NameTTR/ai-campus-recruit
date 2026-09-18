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
        List<String> suggestions,
        List<String> matchedSkills,
        List<String> missingSkills,
        String analysisSource,
        List<String> resumeSkillsSnapshot,
        List<String> requiredSkillsSnapshot
) {
    public MatchResult(
            String matchId,
            String resumeId,
            String jobId,
            String studentId,
            int score,
            List<String> strengths,
            List<String> gaps,
            List<String> suggestions) {
        this(matchId, resumeId, jobId, studentId, score, strengths, gaps, suggestions,
                List.of(), List.of(), "LEGACY", List.of(), List.of());
    }
}
