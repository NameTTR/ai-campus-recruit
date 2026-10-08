package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record RecruitmentContextSnapshot(
        String resumeId,
        String resumeFileName,
        List<String> resumeSkills,
        String jobId,
        String jobTitle,
        List<String> jobRequiredSkills,
        List<String> missingSkills,
        String matchId,
        Integer matchScore,
        Instant validatedAt,
        EvidenceContext evidenceContext) {

    /** Backward-compatible snapshot constructor from the first workspace release. */
    public RecruitmentContextSnapshot(String resumeId, String resumeFileName, List<String> resumeSkills,
                                      String jobId, String jobTitle, List<String> jobRequiredSkills,
                                      List<String> missingSkills, String matchId, Integer matchScore,
                                      Instant validatedAt) {
        this(resumeId, resumeFileName, resumeSkills, jobId, jobTitle, jobRequiredSkills,
                missingSkills, matchId, matchScore, validatedAt, null);
    }
}
