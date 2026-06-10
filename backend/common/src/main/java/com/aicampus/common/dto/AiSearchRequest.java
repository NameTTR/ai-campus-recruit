package com.aicampus.common.dto;

public record AiSearchRequest(String query, String role, Integer limit) {
}
