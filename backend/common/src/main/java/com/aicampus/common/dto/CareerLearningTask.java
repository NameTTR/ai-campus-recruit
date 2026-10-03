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
        String deliverable) {}
