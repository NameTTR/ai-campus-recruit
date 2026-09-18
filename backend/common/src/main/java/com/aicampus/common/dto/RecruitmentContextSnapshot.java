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
        Instant validatedAt) {
}
