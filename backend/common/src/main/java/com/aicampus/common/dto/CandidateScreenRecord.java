package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record CandidateScreenRecord(
        String screeningId,
        String companyId,
        String deliveryId,
        String studentId,
        String jobId,
        int score,
        String recommendation,
        List<String> strengths,
        List<String> risks,
        List<String> interviewQuestions,
        List<String> nextActions,
        boolean mocked,
        Instant createdAt) {
}
