package com.aicampus.common.dto;

import java.util.List;

public record CandidateScreenResult(
        String deliveryId,
        String studentId,
        String jobId,
        int score,
        String recommendation,
        List<String> strengths,
        List<String> risks,
        List<String> interviewQuestions,
        List<String> nextActions,
        boolean mocked) {
}
