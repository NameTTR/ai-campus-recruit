package com.aicampus.common.dto;

import java.util.List;

public record JobPostRequest(
        String companyId,
        String title,
        String city,
        String salaryRange,
        List<String> requiredSkills,
        String description,
        String companyName
) {
    /** Backward-compatible constructor used by existing clients and tests. */
    public JobPostRequest(String companyId, String title, String city, String salaryRange,
            List<String> requiredSkills, String description) {
        this(companyId, title, city, salaryRange, requiredSkills, description, null);
    }
}
