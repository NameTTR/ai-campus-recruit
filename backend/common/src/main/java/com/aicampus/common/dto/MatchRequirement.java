package com.aicampus.common.dto;
public record MatchRequirement(String skill, boolean declared, boolean supported, String status, SkillEvidence evidence, String suggestion,
                               String requirementTier, String requirementQuote, String evidenceState, String nextStep) {
    /** Legacy six-field wire contract. */
    public MatchRequirement(String skill, boolean declared, boolean supported, String status,
                            SkillEvidence evidence, String suggestion) {
        this(skill, declared, supported, status, evidence, suggestion, "UNSPECIFIED", "",
                legacyEvidenceState(declared, supported), suggestion);
    }

    private static String legacyEvidenceState(boolean declared, boolean supported) {
        if (supported) return "RESUME_EVIDENCE";
        if (declared) return "STUDENT_DECLARED";
        return "NO_BASIS";
    }
}
