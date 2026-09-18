package com.aicampus.common.dto;

import java.util.List;

public record ResumeProfileUpdateRequest(
        String education,
        List<String> skills,
        List<String> projects
) {
}
