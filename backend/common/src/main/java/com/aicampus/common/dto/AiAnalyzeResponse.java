package com.aicampus.common.dto;

public record AiAnalyzeResponse(String taskType, String provider, String content, boolean mocked) {
}

