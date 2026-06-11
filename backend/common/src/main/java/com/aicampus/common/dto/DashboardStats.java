package com.aicampus.common.dto;

import com.aicampus.common.enums.DeliveryStatus;
import java.util.List;
import java.util.Map;

public record DashboardStats(
        long studentCount,
        long companyCount,
        long jobCount,
        long deliveryCount,
        int averageMatchScore,
        Map<DeliveryStatus, Long> deliveryStatusCounts,
        long pendingDeliveryCount,
        int interviewRate,
        int offerRate,
        long activeStudentCount,
        long highPotentialCandidateCount,
        List<TrendPoint> weeklyDeliveryTrend,
        List<SkillDemand> skillDemandTop,
        List<FunnelStage> conversionFunnel,
        List<String> riskAlerts
) {
    public record TrendPoint(String label, long deliveryCount, long interviewCount, long offerCount) {
    }

    public record SkillDemand(String skill, long jobCount, long matchedStudentCount, int demandScore) {
    }

    public record FunnelStage(String stage, String label, long count, int conversionRate) {
    }
}
