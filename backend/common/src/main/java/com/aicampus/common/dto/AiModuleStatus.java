package com.aicampus.common.dto;

import java.util.List;

public record AiModuleStatus(
        String provider,
        String model,
        boolean configured,
        String baseUrl,
        List<String> capabilities,
        String fallbackReason) {
}
