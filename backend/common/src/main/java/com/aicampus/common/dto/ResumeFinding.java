package com.aicampus.common.dto;

import java.util.List;

public record ResumeFinding(
        String category,
        String originalQuote,
        String issue,
        String suggestedRewrite,
        String basis,
        String sourceReference,
        String requiredSkill,
        int priority,
        String requirementLevel,
        String factUnitId,
        List<String> followUpQuestions) {
    public ResumeFinding {
        requirementLevel = requirementLevel == null ? "UNSPECIFIED" : requirementLevel;
        followUpQuestions = followUpQuestions == null ? List.of()
                : followUpQuestions.stream().filter(q -> q != null && !q.isBlank()).distinct().limit(3).toList();
    }

    /** Historic snapshots and model responses can continue using the original seven fields. */
    public ResumeFinding(String category, String originalQuote, String issue, String suggestedRewrite,
            String basis, String sourceReference, String requiredSkill) {
        this(category, originalQuote, issue, suggestedRewrite, basis, sourceReference, requiredSkill,
                0, "UNSPECIFIED", null, List.of());
    }
}
