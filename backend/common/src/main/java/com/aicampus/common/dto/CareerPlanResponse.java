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
        boolean mocked,
        List<CareerLearningTask> tasks) {
    public CareerPlanResponse(
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
        this(
                studentId,
                targetRole,
                readinessScore,
                summary,
                milestones,
                skillGaps,
                weeklyActions,
                portfolioTasks,
                interviewFocus,
                mocked,
                List.of());
    }

    public record Milestone(String title, String timeframe, List<String> goals) {}
}
