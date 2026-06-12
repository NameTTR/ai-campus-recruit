package com.aicampus.common.dto;

public record KnowledgeAnswerRequest(String query, String role, Integer limit, Boolean useAi) {
}
