package com.aicampus.common.dto;

import java.util.List;

/** A source-bound fact; all descriptive fields are verbatim excerpts, never model inventions. */
public record ResumeFactUnit(
        String id,
        String sourceKind,
        String sourceReference,
        String sourceVersion,
        String originalQuote,
        int startOffset,
        int endOffset,
        String personalAction,
        String methodOrTechnology,
        String projectScope,
        String validationProcess,
        String result,
        boolean dataMissing,
        String evidenceStatus,
        List<String> skills) {
    public ResumeFactUnit {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
