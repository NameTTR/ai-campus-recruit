package com.aicampus.common.dto;

import java.util.List;

public record ResumeRewriteRequest(
        String studentId,
        String resumeId,
        String targetRole,
        String resumeSummary,
        List<String> skills,
        List<String> projects) {
}
