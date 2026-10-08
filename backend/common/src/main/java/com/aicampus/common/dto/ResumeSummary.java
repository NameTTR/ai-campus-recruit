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
        StructuredResumeDiagnosis structuredDiagnosis,
        String contentFingerprint
) {
    /** Additive source digest; legacy JSON and fifteen-field constructors remain supported. */
    public ResumeSummary(String resumeId, String studentId, String fileName, String education, List<String> skills,
            List<String> projects, String diagnosis, int score, String objectKey, String storageProvider,
            String storageStatus, String sourceFormat, String parseStatus, int parsedTextLength,
            StructuredResumeDiagnosis structuredDiagnosis) {
        this(resumeId, studentId, fileName, education, skills, projects, diagnosis, score, objectKey,
                storageProvider, storageStatus, sourceFormat, parseStatus, parsedTextLength, structuredDiagnosis, null);
    }

    public ResumeSummary(String resumeId, String studentId, String fileName, String education, List<String> skills,
            List<String> projects, String diagnosis, int score, String objectKey, String storageProvider,
            String storageStatus, String sourceFormat, String parseStatus, int parsedTextLength) {
        this(resumeId, studentId, fileName, education, skills, projects, diagnosis, score, objectKey,
                storageProvider, storageStatus, sourceFormat, parseStatus, parsedTextLength, null);
    }

    public ResumeSummary withContentFingerprint(String value) {
        return new ResumeSummary(resumeId, studentId, fileName, education, skills, projects, diagnosis,
                score, objectKey, storageProvider, storageStatus, sourceFormat, parseStatus,
                parsedTextLength, structuredDiagnosis, value);
    }
}
