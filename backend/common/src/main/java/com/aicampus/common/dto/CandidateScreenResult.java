package com.aicampus.common.dto;

import java.util.List;

public record CandidateScreenResult(
        String deliveryId,
        String studentId,
        String jobId,
        String resumeSourceFormat,
        String resumeParseStatus,
        int resumeParsedTextLength,
        int score,
        String recommendation,
        List<String> strengths,
        List<String> risks,
        List<String> interviewQuestions,
        List<String> nextActions,
        boolean mocked) {
    public CandidateScreenResult {
        if (resumeSourceFormat == null || resumeSourceFormat.isBlank()) {
            resumeSourceFormat = "UNKNOWN";
        }
        if (resumeParseStatus == null || resumeParseStatus.isBlank()) {
            resumeParseStatus = "UNKNOWN";
        }
        if (resumeParsedTextLength < 0) {
            resumeParsedTextLength = 0;
        }
    }

    public CandidateScreenResult(
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
        this(
                deliveryId,
                studentId,
                jobId,
                "UNKNOWN",
                "UNKNOWN",
                0,
                score,
                recommendation,
                strengths,
                risks,
                interviewQuestions,
                nextActions,
                mocked);
    }
}
