package com.aicampus.common.dto;

public record KnowledgeSearchRequest(String query, String role, Integer limit) {
}
