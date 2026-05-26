package com.aicampus.common.dto;

public record DashboardStats(
        long studentCount,
        long companyCount,
        long jobCount,
        long deliveryCount,
        int averageMatchScore
) {
}

