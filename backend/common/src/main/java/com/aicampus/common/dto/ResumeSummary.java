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
        int score,
        String objectKey,
        String storageProvider,
        String storageStatus,
        String sourceFormat,
        String parseStatus,
        int parsedTextLength,
        StructuredResumeDiagnosis structuredDiagnosis
) {
    public ResumeSummary(String resumeId, String studentId, String fileName, String education, List<String> skills,
            List<String> projects, String diagnosis, int score, String objectKey, String storageProvider,
            String storageStatus, String sourceFormat, String parseStatus, int parsedTextLength) {
        this(resumeId, studentId, fileName, education, skills, projects, diagnosis, score, objectKey,
                storageProvider, storageStatus, sourceFormat, parseStatus, parsedTextLength, null);
    }
}
