package com.aicampus.common.dto;

import java.util.List;

public record InterviewResumeCandidate(String candidateId, String sessionId, String questionId,
        String attemptId, String title, String background, String actions, String methods,
        String validation, String results, List<String> missingData,
        List<InterviewSourceReference> sourceReferences, boolean pending) {}
