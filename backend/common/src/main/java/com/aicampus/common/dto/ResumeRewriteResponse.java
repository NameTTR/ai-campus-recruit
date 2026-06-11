package com.aicampus.common.dto;

import java.util.List;

public record ResumeRewriteResponse(
        String studentId,
        String resumeId,
        String targetRole,
        String improvedSummary,
        List<String> rewrittenProjects,
        List<String> keywordSuggestions,
        List<String> missingEvidence,
        List<String> actionChecklist,
        boolean mocked) {
}
