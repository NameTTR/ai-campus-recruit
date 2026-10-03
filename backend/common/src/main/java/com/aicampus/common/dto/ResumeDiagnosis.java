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
        String resumeTextSnapshot,
        StructuredResumeDiagnosis details
) {
    public ResumeDiagnosis(String diagnosisId, String resumeId, String studentId, String targetJob, String diagnosis,
            int score, String source, Instant createdAt, String educationSnapshot, List<String> skillsSnapshot,
            List<String> projectsSnapshot, String resumeTextSnapshot) {
        this(diagnosisId, resumeId, studentId, targetJob, diagnosis, score, source, createdAt, educationSnapshot,
                skillsSnapshot, projectsSnapshot, resumeTextSnapshot, null);
    }
    public ResumeDiagnosis {
        skillsSnapshot = skillsSnapshot == null ? List.of() : List.copyOf(skillsSnapshot);
        projectsSnapshot = projectsSnapshot == null ? List.of() : List.copyOf(projectsSnapshot);
        resumeTextSnapshot = resumeTextSnapshot == null ? "" : resumeTextSnapshot;
    }
}
