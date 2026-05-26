package com.aicampus.common.dto;

import java.util.List;

public record JobSummary(
        String jobId,
        String companyId,
        String companyName,
        String title,
        String city,
        String salaryRange,
        List<String> requiredSkills,
        String description,
        String aiSummary
) {
}

