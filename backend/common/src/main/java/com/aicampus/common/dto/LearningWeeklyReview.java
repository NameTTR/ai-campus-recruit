package com.aicampus.common.dto;

import java.util.List;

/** A deterministic weekly review used as input to the next replan. */
public record LearningWeeklyReview(
        String planId,
        int week,
        int plannedMinutes,
        int actualMinutes,
        int completedTasks,
        int delayedTasks,
        List<String> weakSkills,
        List<String> nextActions,
        String incompleteReason,
        String hardestTask,
        boolean needsSplit,
        int mastery,
        int nextWeekMinutes,
        List<String> newProblems) {
    public LearningWeeklyReview(
            String planId,
            int week,
            int plannedMinutes,
            int actualMinutes,
            int completedTasks,
            int delayedTasks,
            List<String> weakSkills,
            List<String> nextActions) {
        this(planId, week, plannedMinutes, actualMinutes, completedTasks, delayedTasks,
                weakSkills, nextActions, null, null, false, 0, 0, List.of());
    }
}
