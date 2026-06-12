package com.aicampus.common.dto;

import java.util.List;

public record AiCoachAdviceRequest(
        String studentId,
        String targetRole,
        List<String> skills,
        List<String> recentDeliveries,
        List<String> interviewWeaknesses,
        String careerGoal,
        Integer weeks) {
}

