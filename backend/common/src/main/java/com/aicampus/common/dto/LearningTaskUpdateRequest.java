package com.aicampus.common.dto;

public record LearningTaskUpdateRequest(
        String status, String feedback, Integer actualMinutes, String deferredUntil) {
    public LearningTaskUpdateRequest(String status, String feedback) {
        this(status, feedback, null, null);
    }
}
