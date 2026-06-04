package com.aicampus.common.dto;

import java.util.List;

public record CandidateScreenRequest(
        String deliveryId,
        String companyId,
        String studentId,
        String resumeId,
        String jobId,
        String resumeSourceFormat,
        String resumeParseStatus,
        int resumeParsedTextLength,
        String targetRole,
        List<String> skills,
        List<String> projects,
        List<String> jobRequirements,
        String resumeSummary,
        String jobDescription) {
    private static final String DEFAULT_COMPANY_ID = "C001";
    private static final String UNKNOWN = "UNKNOWN";

    public CandidateScreenRequest {
        if (companyId == null || companyId.isBlank()) {
            companyId = DEFAULT_COMPANY_ID;
        }
        if (resumeSourceFormat == null || resumeSourceFormat.isBlank()) {
            resumeSourceFormat = UNKNOWN;
        }
        if (resumeParseStatus == null || resumeParseStatus.isBlank()) {
            resumeParseStatus = UNKNOWN;
        }
        if (resumeParsedTextLength < 0) {
            resumeParsedTextLength = 0;
        }
    }

    public CandidateScreenRequest(
            String deliveryId,
            String companyId,
            String studentId,
            String resumeId,
            String jobId,
            String targetRole,
            List<String> skills,
            List<String> projects,
            List<String> jobRequirements,
            String resumeSummary,
            String jobDescription) {
        this(
                deliveryId,
                companyId,
                studentId,
                resumeId,
                jobId,
                UNKNOWN,
                UNKNOWN,
                0,
                targetRole,
                skills,
                projects,
                jobRequirements,
                resumeSummary,
                jobDescription);
    }

    public CandidateScreenRequest(
            String deliveryId,
            String studentId,
            String resumeId,
            String jobId,
            String resumeSourceFormat,
            String resumeParseStatus,
            int resumeParsedTextLength,
            String targetRole,
            List<String> skills,
            List<String> projects,
            List<String> jobRequirements,
            String resumeSummary,
            String jobDescription) {
        this(
                deliveryId,
                DEFAULT_COMPANY_ID,
                studentId,
                resumeId,
                jobId,
                resumeSourceFormat,
                resumeParseStatus,
                resumeParsedTextLength,
                targetRole,
                skills,
                projects,
                jobRequirements,
                resumeSummary,
                jobDescription);
    }

    public CandidateScreenRequest(
            String deliveryId,
            String studentId,
            String resumeId,
            String jobId,
            String targetRole,
            List<String> skills,
            List<String> projects,
            List<String> jobRequirements,
            String resumeSummary,
            String jobDescription) {
        this(
                deliveryId,
                DEFAULT_COMPANY_ID,
                studentId,
                resumeId,
                jobId,
                UNKNOWN,
                UNKNOWN,
                0,
                targetRole,
                skills,
                projects,
                jobRequirements,
                resumeSummary,
                jobDescription);
    }
}
