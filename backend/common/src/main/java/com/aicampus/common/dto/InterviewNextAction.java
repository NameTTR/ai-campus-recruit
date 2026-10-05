package com.aicampus.common.dto;

import java.util.List;

public record InterviewNextAction(String actionId, String type, String title, String description,
        String skill, int estimatedMinutes, List<InterviewSourceReference> sourceReferences) {}
