package com.aicampus.common.dto;

import java.util.List;

public record CandidateScreenRequest(
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
}
