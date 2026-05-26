package com.aicampus.common.dto;

import java.util.List;

public record ResumeSummary(
        String resumeId,
        String studentId,
        String fileName,
        String education,
        List<String> skills,
        List<String> projects,
        String diagnosis,
        int score
) {
}

