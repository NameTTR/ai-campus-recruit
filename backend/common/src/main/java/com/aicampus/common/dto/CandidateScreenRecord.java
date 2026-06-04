package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record CandidateScreenRecord(
        String screeningId,
        String companyId,
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
        boolean mocked,
        Instant createdAt) {
    public CandidateScreenRecord {
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

    public CandidateScreenRecord(
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
        this(
                screeningId,
                companyId,
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
                mocked,
                createdAt);
    }
}
