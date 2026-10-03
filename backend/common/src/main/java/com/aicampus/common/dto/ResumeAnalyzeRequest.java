package com.aicampus.common.dto;
public record ResumeAnalyzeRequest(String resumeId, String resumeText, String targetJob, String jobId) {
 public ResumeAnalyzeRequest(String resumeId, String resumeText, String targetJob) { this(resumeId, resumeText, targetJob, null); }
}
