package com.aicampus.common.dto;

import java.util.List;

public record CareerPlanResponse(
        String studentId,
        String targetRole,
        int readinessScore,
        String summary,
        List<Milestone> milestones,
        List<String> skillGaps,
        List<String> weeklyActions,
        List<String> portfolioTasks,
        List<String> interviewFocus,
        boolean mocked) {
    public record Milestone(
            String title,
            String timeframe,
            List<String> goals) {
    }
}
