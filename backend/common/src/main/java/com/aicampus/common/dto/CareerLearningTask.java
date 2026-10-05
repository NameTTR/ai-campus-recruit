package com.aicampus.common.dto;

import java.util.List;

public record CareerLearningTask(
        int week,
        String title,
        String targetSkill,
        List<String> prerequisites,
        int estimatedHours,
        String exercise,
        String acceptanceCriteria,
        String deliverable,
        Integer estimatedMinutes) {
    public CareerLearningTask(int week, String title, String targetSkill,
            List<String> prerequisites, int estimatedHours, String exercise,
            String acceptanceCriteria, String deliverable) {
        this(week, title, targetSkill, prerequisites, estimatedHours, exercise,
                acceptanceCriteria, deliverable, null);
    }

    public int durationMinutes() {
        return estimatedMinutes == null ? Math.multiplyExact(estimatedHours, 60) : estimatedMinutes;
    }
}
