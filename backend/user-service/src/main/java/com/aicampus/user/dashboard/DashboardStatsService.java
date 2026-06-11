package com.aicampus.user.dashboard;

import com.aicampus.common.dto.DashboardStats;
import com.aicampus.common.enums.DeliveryStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class DashboardStatsService {
    private static final Logger log = LoggerFactory.getLogger(DashboardStatsService.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final Environment environment;
    private final ObjectMapper objectMapper;

    public DashboardStatsService(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public DashboardStats dashboard() {
        if (!realtimeEnabled()) {
            return fallback();
        }
        String url = property("spring.datasource.url", "SPRING_DATASOURCE_URL");
        if (url == null || url.isBlank()) {
            return fallback();
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            log.warn("MySQL driver is unavailable; dashboard returns fallback data", ex);
            return fallback();
        }

        String username = property("spring.datasource.username", "SPRING_DATASOURCE_USERNAME");
        String password = property("spring.datasource.password", "SPRING_DATASOURCE_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            return aggregate(connection);
        } catch (Exception ex) {
            log.warn("Failed to aggregate dashboard stats from datasource; returning fallback data", ex);
            return fallback();
        }
    }

    DashboardStats aggregate(Connection connection) throws SQLException {
        Set<String> studentIds = new LinkedHashSet<>();
        Set<String> companyIds = new LinkedHashSet<>();
        Set<String> activeStudentIds = new LinkedHashSet<>();
        Set<String> highPotentialStudentIds = new LinkedHashSet<>();

        addColumnValues(connection, "resume_summary_record", "student_id", studentIds);
        addColumnValues(connection, "delivery_record", "student_id", studentIds);
        addColumnValues(connection, "match_result_record", "student_id", studentIds);
        addColumnValues(connection, "ai_candidate_screen_record", "student_id", studentIds);
        addColumnValues(connection, "ai_planning_record", "student_id", studentIds);

        addColumnValues(connection, "job_record", "company_id", companyIds);
        addColumnValues(connection, "delivery_record", "company_id", companyIds);
        addColumnValues(connection, "ai_candidate_screen_record", "company_id", companyIds);

        addRecentStudents(connection, "resume_summary_record", "student_id", "updated_at", activeStudentIds);
        addRecentStudents(connection, "delivery_record", "student_id", "created_at", activeStudentIds);
        addRecentStudents(connection, "match_result_record", "student_id", "created_at", activeStudentIds);
        addRecentStudents(connection, "ai_candidate_screen_record", "student_id", "created_at", activeStudentIds);
        addRecentStudents(connection, "ai_planning_record", "student_id", "created_at", activeStudentIds);

        addHighPotentialStudents(connection, "match_result_record", highPotentialStudentIds);
        addHighPotentialStudents(connection, "ai_candidate_screen_record", highPotentialStudentIds);

        EnumMap<DeliveryStatus, Long> statusCounts = deliveryStatusCounts(connection);
        long deliveryCount = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long interviewCount = statusCounts.get(DeliveryStatus.INTERVIEW) + statusCounts.get(DeliveryStatus.OFFER);
        long offerCount = statusCounts.get(DeliveryStatus.OFFER);
        long jobCount = countTable(connection, "job_record");
        int averageMatchScore = averageScore(connection);
        List<DashboardStats.TrendPoint> trend = weeklyTrend(connection);
        List<DashboardStats.SkillDemand> skillDemand = skillDemand(connection);

        return new DashboardStats(
                studentIds.size(),
                companyIds.size(),
                jobCount,
                deliveryCount,
                averageMatchScore,
                statusCounts,
                statusCounts.get(DeliveryStatus.SUBMITTED),
                percent(interviewCount, deliveryCount),
                percent(offerCount, deliveryCount),
                activeStudentIds.size(),
                highPotentialStudentIds.size(),
                trend,
                skillDemand,
                List.of(
                        new DashboardStats.FunnelStage("SUBMITTED", "投递", deliveryCount, deliveryCount > 0 ? 100 : 0),
                        new DashboardStats.FunnelStage("VIEWED", "已查看", statusCounts.get(DeliveryStatus.VIEWED), percent(statusCounts.get(DeliveryStatus.VIEWED), deliveryCount)),
                        new DashboardStats.FunnelStage("INTERVIEW", "进入面试", interviewCount, percent(interviewCount, deliveryCount)),
                        new DashboardStats.FunnelStage("OFFER", "录用", offerCount, percent(offerCount, deliveryCount))),
                riskAlerts(statusCounts, deliveryCount, activeStudentIds.size(), highPotentialStudentIds.size(), skillDemand));
    }

    private boolean realtimeEnabled() {
        String value = property("dashboard.realtime.enabled", "DASHBOARD_REALTIME_ENABLED");
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    private String property(String springName, String envName) {
        String value = environment.getProperty(springName);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        return value;
    }

    private void addColumnValues(Connection connection, String table, String column, Set<String> values) throws SQLException {
        if (!tableExists(connection, table)) {
            return;
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT DISTINCT " + column + " FROM " + table)) {
            while (resultSet.next()) {
                String value = resultSet.getString(1);
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
    }

    private void addRecentStudents(Connection connection, String table, String studentColumn, String timeColumn, Set<String> values)
            throws SQLException {
        if (!tableExists(connection, table)) {
            return;
        }
        String sql = "SELECT DISTINCT " + studentColumn + " FROM " + table
                + " WHERE " + timeColumn + " >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String value = resultSet.getString(1);
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
    }

    private void addHighPotentialStudents(Connection connection, String table, Set<String> values) throws SQLException {
        if (!tableExists(connection, table)) {
            return;
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT DISTINCT student_id FROM " + table + " WHERE score >= 85")) {
            while (resultSet.next()) {
                String value = resultSet.getString(1);
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
    }

    private EnumMap<DeliveryStatus, Long> deliveryStatusCounts(Connection connection) throws SQLException {
        EnumMap<DeliveryStatus, Long> counts = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            counts.put(status, 0L);
        }
        if (!tableExists(connection, "delivery_record")) {
            return counts;
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT status, COUNT(*) FROM delivery_record GROUP BY status")) {
            while (resultSet.next()) {
                try {
                    counts.put(DeliveryStatus.valueOf(resultSet.getString(1)), resultSet.getLong(2));
                } catch (IllegalArgumentException ignored) {
                    // Ignore unknown status values left by manual database edits.
                }
            }
        }
        return counts;
    }

    private long countTable(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)) {
            return 0;
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private int averageScore(Connection connection) throws SQLException {
        long total = 0;
        long count = 0;
        if (tableExists(connection, "match_result_record")) {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT AVG(score), COUNT(*) FROM match_result_record")) {
                if (resultSet.next()) {
                    total += Math.round(resultSet.getDouble(1) * resultSet.getLong(2));
                    count += resultSet.getLong(2);
                }
            }
        }
        if (count == 0 && tableExists(connection, "ai_candidate_screen_record")) {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT AVG(score), COUNT(*) FROM ai_candidate_screen_record")) {
                if (resultSet.next()) {
                    total += Math.round(resultSet.getDouble(1) * resultSet.getLong(2));
                    count += resultSet.getLong(2);
                }
            }
        }
        return count == 0 ? 0 : (int) Math.round(total * 1.0 / count);
    }

    private List<DashboardStats.TrendPoint> weeklyTrend(Connection connection) throws SQLException {
        if (!tableExists(connection, "delivery_record")) {
            return List.of();
        }
        Map<String, TrendAccumulator> points = new LinkedHashMap<>();
        String sql = """
                SELECT DATE_FORMAT(created_at, '%m-%d') AS label, status, COUNT(*) AS count
                FROM delivery_record
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
                GROUP BY DATE(created_at), label, status
                ORDER BY DATE(created_at)
                """;
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String label = resultSet.getString("label");
                String status = resultSet.getString("status");
                long count = resultSet.getLong("count");
                TrendAccumulator accumulator = points.computeIfAbsent(label, ignored -> new TrendAccumulator());
                accumulator.deliveryCount += count;
                if ("INTERVIEW".equals(status)) {
                    accumulator.interviewCount += count;
                } else if ("OFFER".equals(status)) {
                    accumulator.interviewCount += count;
                    accumulator.offerCount += count;
                }
            }
        }
        return points.entrySet().stream()
                .map(entry -> new DashboardStats.TrendPoint(
                        entry.getKey(),
                        entry.getValue().deliveryCount,
                        entry.getValue().interviewCount,
                        entry.getValue().offerCount))
                .toList();
    }

    private List<DashboardStats.SkillDemand> skillDemand(Connection connection) throws SQLException {
        if (!tableExists(connection, "job_record")) {
            return List.of();
        }
        Map<String, Set<String>> jobSkills = new LinkedHashMap<>();
        Map<String, SkillAccumulator> skillMap = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT job_id, required_skills FROM job_record")) {
            while (resultSet.next()) {
                String jobId = resultSet.getString("job_id");
                Set<String> skills = new LinkedHashSet<>(readSkills(resultSet.getString("required_skills")));
                jobSkills.put(jobId, skills);
                for (String skill : skills) {
                    skillMap.computeIfAbsent(skill, ignored -> new SkillAccumulator()).jobIds.add(jobId);
                }
            }
        }
        if (tableExists(connection, "match_result_record") && !jobSkills.isEmpty()) {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT job_id, student_id, score FROM match_result_record WHERE score >= 70")) {
                while (resultSet.next()) {
                    Set<String> skills = jobSkills.get(resultSet.getString("job_id"));
                    if (skills == null) {
                        continue;
                    }
                    String studentId = resultSet.getString("student_id");
                    for (String skill : skills) {
                        skillMap.computeIfAbsent(skill, ignored -> new SkillAccumulator()).matchedStudentIds.add(studentId);
                    }
                }
            }
        }
        return skillMap.entrySet().stream()
                .map(entry -> {
                    int jobCount = entry.getValue().jobIds.size();
                    int matchedStudentCount = entry.getValue().matchedStudentIds.size();
                    int score = Math.min(100, Math.max(1, jobCount * 12 + matchedStudentCount * 2));
                    return new DashboardStats.SkillDemand(entry.getKey(), jobCount, matchedStudentCount, score);
                })
                .sorted(Comparator.comparing(DashboardStats.SkillDemand::demandScore).reversed()
                        .thenComparing(DashboardStats.SkillDemand::jobCount).reversed()
                        .thenComparing(DashboardStats.SkillDemand::skill))
                .limit(8)
                .toList();
    }

    private List<String> readSkills(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE).stream()
                    .filter(skill -> skill != null && !skill.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (Exception ex) {
            return List.of(value.split("[,，;；/|\\s]+")).stream()
                    .map(String::trim)
                    .filter(skill -> !skill.isBlank())
                    .toList();
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(connection.getCatalog(), null, table, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        }
    }

    private List<String> riskAlerts(
            EnumMap<DeliveryStatus, Long> statusCounts,
            long deliveryCount,
            int activeStudentCount,
            int highPotentialCount,
            List<DashboardStats.SkillDemand> skillDemand) {
        List<String> alerts = new ArrayList<>();
        long pending = statusCounts.get(DeliveryStatus.SUBMITTED);
        if (deliveryCount > 0 && percent(pending, deliveryCount) >= 20) {
            alerts.add("待处理投递占比达到 " + percent(pending, deliveryCount) + "%，建议提醒企业 48 小时内完成初筛。");
        }
        if (!skillDemand.isEmpty()) {
            DashboardStats.SkillDemand top = skillDemand.get(0);
            alerts.add(top.skill() + " 岗位需求最高，涉及 " + top.jobCount() + " 个岗位，建议同步安排专项辅导。");
        }
        if (activeStudentCount > 0 && highPotentialCount * 4 < activeStudentCount) {
            alerts.add("高潜候选人占活跃学生比例偏低，建议优先辅导简历证据不足和匹配分不足的学生。");
        }
        if (alerts.isEmpty()) {
            alerts.add("当前投递、面试和录用转化暂无明显风险，继续跟踪本周趋势。");
        }
        return alerts;
    }

    private static int percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }

    private static DashboardStats fallback() {
        EnumMap<DeliveryStatus, Long> deliveryStatusCounts = new EnumMap<>(DeliveryStatus.class);
        deliveryStatusCounts.put(DeliveryStatus.SUBMITTED, 72L);
        deliveryStatusCounts.put(DeliveryStatus.VIEWED, 96L);
        deliveryStatusCounts.put(DeliveryStatus.INTERVIEW, 84L);
        deliveryStatusCounts.put(DeliveryStatus.OFFER, 28L);
        deliveryStatusCounts.put(DeliveryStatus.REJECTED, 32L);
        long deliveryCount = 312;
        long interviewCount = deliveryStatusCounts.get(DeliveryStatus.INTERVIEW) + deliveryStatusCounts.get(DeliveryStatus.OFFER);
        long offerCount = deliveryStatusCounts.get(DeliveryStatus.OFFER);
        return new DashboardStats(
                128,
                24,
                56,
                deliveryCount,
                82,
                deliveryStatusCounts,
                deliveryStatusCounts.get(DeliveryStatus.SUBMITTED),
                percent(interviewCount, deliveryCount),
                percent(offerCount, deliveryCount),
                96,
                38,
                List.of(
                        new DashboardStats.TrendPoint("06-01", 42, 18, 4),
                        new DashboardStats.TrendPoint("06-02", 48, 21, 5),
                        new DashboardStats.TrendPoint("06-03", 56, 24, 6),
                        new DashboardStats.TrendPoint("06-04", 61, 27, 7),
                        new DashboardStats.TrendPoint("06-05", 53, 23, 6),
                        new DashboardStats.TrendPoint("06-06", 52, 19, 5)),
                List.of(
                        new DashboardStats.SkillDemand("Java", 38, 74, 92),
                        new DashboardStats.SkillDemand("Spring Boot", 34, 61, 88),
                        new DashboardStats.SkillDemand("MySQL", 31, 58, 84),
                        new DashboardStats.SkillDemand("Redis", 24, 39, 76),
                        new DashboardStats.SkillDemand("Docker", 18, 33, 68)),
                List.of(
                        new DashboardStats.FunnelStage("SUBMITTED", "投递", deliveryCount, 100),
                        new DashboardStats.FunnelStage("VIEWED", "已查看", deliveryStatusCounts.get(DeliveryStatus.VIEWED), percent(deliveryStatusCounts.get(DeliveryStatus.VIEWED), deliveryCount)),
                        new DashboardStats.FunnelStage("INTERVIEW", "进入面试", interviewCount, percent(interviewCount, deliveryCount)),
                        new DashboardStats.FunnelStage("OFFER", "录用", offerCount, percent(offerCount, deliveryCount))),
                List.of(
                        "待处理投递占比仍较高，建议就业办提醒企业 48 小时内完成初筛。",
                        "Redis、Docker 与 RocketMQ 技能供给低于岗位需求，可安排专项辅导。",
                        "高潜候选人需要尽快进入模拟面试和企业推荐流程。"));
    }

    private static final class TrendAccumulator {
        private long deliveryCount;
        private long interviewCount;
        private long offerCount;
    }

    private static final class SkillAccumulator {
        private final Set<String> jobIds = new LinkedHashSet<>();
        private final Set<String> matchedStudentIds = new LinkedHashSet<>();
    }
}
