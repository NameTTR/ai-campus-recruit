package com.aicampus.common.dto;

import java.util.List;

public record InterviewQuestionRequest(
        String studentId,
        String resumeId,
        String jobId,
        String targetRole,
        List<String> skills,
        Integer questionCount,
        Boolean useRag,
        Integer knowledgeLimit) {
    public InterviewQuestionRequest(
            String studentId,
            String resumeId,
            String jobId,
            String targetRole,
            List<String> skills) {
        this(studentId, resumeId, jobId, targetRole, skills, null, null, null);
    }
}
