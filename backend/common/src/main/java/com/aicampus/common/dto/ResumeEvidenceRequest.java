package com.aicampus.common.dto;
public record ResumeEvidenceRequest(String resumeId, String studentId, String targetJob, JobSummary job, ResumeProfileSnapshot profile, String inputFingerprint) {}
