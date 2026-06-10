package com.aicampus.common.dto;

import java.time.Instant;
import java.util.List;

public record AiSearchResponse(String query, List<AiSearchResult> results, Instant generatedAt) {
}
