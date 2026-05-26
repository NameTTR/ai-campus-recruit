package com.aicampus.common.dto;

import java.util.List;

public record JobPostRequest(
        String companyId,
        String title,
        String city,
        String salaryRange,
        List<String> requiredSkills,
        String description
) {
}

