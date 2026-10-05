package com.aicampus.common.dto;

import java.util.List;

public record InterviewAttemptComparison(String questionId, String originalAttemptId,
        String selectedAttemptId, List<String> improvements, List<String> remainingGaps,
        String note) {}
