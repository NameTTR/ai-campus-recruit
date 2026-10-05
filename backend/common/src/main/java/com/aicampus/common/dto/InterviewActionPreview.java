package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record InterviewActionPreview(String previewId, String actionId, String type, String title,
        String description, int estimatedMinutes, String impact, String status,
        String planId, String createdSessionId, String createdPlanId,
        List<InterviewSourceReference> sourceReferences, Instant createdAt,
        String reportFingerprint, String skillGap) {
    public InterviewActionPreview(String previewId, String actionId, String type, String title,
            String description, int estimatedMinutes, String impact, String status,
            String planId, String createdSessionId, String createdPlanId,
            List<InterviewSourceReference> sourceReferences, Instant createdAt) {
        this(previewId, actionId, type, title, description, estimatedMinutes, impact, status,
                planId, createdSessionId, createdPlanId, sourceReferences, createdAt, null, null);
    }
}
