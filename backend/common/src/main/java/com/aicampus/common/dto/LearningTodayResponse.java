package com.aicampus.common.dto;

import java.util.List;

/** Tasks and reminders for a student's selected day. */
public record LearningTodayResponse(
        String planId,
        String date,
        List<LearningTask> tasks,
        List<String> reminders,
        int plannedMinutes,
        int actualMinutes) {}
