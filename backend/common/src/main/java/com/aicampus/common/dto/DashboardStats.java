package com.aicampus.common.dto;

import com.aicampus.common.enums.DeliveryStatus;
import java.util.Map;

public record DashboardStats(
        long studentCount,
        long companyCount,
        long jobCount,
        long deliveryCount,
        int averageMatchScore,
        Map<DeliveryStatus, Long> deliveryStatusCounts,
        long pendingDeliveryCount
) {
}
