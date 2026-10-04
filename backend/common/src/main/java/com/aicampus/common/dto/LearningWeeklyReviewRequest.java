package com.aicampus.common.dto;

import java.util.List;

/** Student supplied weekly retrospective used to preview the next plan revision. */
public record LearningWeeklyReviewRequest(
        Integer week,
        Integer plannedMinutes,
        Integer actualMinutes,
        Integer completedTasks,
        String incompleteReason,
        String hardestTask,
        Boolean needsSplit,
        Integer mastery,
        Integer nextWeekMinutes,
        List<String> newProblems) {}
