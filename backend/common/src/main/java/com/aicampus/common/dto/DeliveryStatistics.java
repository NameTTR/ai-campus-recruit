package com.aicampus.common.dto;

import com.aicampus.common.enums.DeliveryStatus;
import java.util.Map;

public record DeliveryStatistics(
        long totalCount,
        Map<DeliveryStatus, Long> statusCounts,
        long pendingCount
) {
}
