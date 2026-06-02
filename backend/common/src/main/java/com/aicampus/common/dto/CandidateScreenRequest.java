package com.aicampus.common.dto;

import java.util.List;

public record CandidateScreenRequest(
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
    private static final String DEFAULT_COMPANY_ID = "C001";

    public CandidateScreenRequest {
        if (companyId == null || companyId.isBlank()) {
            companyId = DEFAULT_COMPANY_ID;
        }
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
                targetRole,
                skills,
                projects,
                jobRequirements,
                resumeSummary,
                jobDescription);
    }
}
