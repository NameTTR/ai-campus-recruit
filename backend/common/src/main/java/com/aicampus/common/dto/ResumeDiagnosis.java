package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record ResumeDiagnosis(
        String diagnosisId,
        String resumeId,
        String studentId,
        String targetJob,
        String diagnosis,
        int score,
        String source,
        Instant createdAt,
        String educationSnapshot,
        List<String> skillsSnapshot,
        List<String> projectsSnapshot,
        String resumeTextSnapshot
) {
    public ResumeDiagnosis {
        skillsSnapshot = skillsSnapshot == null ? List.of() : List.copyOf(skillsSnapshot);
        projectsSnapshot = projectsSnapshot == null ? List.of() : List.copyOf(projectsSnapshot);
        resumeTextSnapshot = resumeTextSnapshot == null ? "" : resumeTextSnapshot;
    }
}
