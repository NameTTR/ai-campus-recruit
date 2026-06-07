package com.aicampus.user.admin;

import java.time.Instant;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminDeploymentTopologyService {
    private static final String CONFIGURED = "CONFIGURED";
    private static final String OPTIONAL = "OPTIONAL";
    private static final String UNKNOWN = "UNKNOWN";

    private final Environment environment;

    public AdminDeploymentTopologyService(Environment environment) {
        this.environment = environment;
    }

    public AdminDeploymentTopology topology() {
        String vm1Host = config("vm1.host", "VM1_HOST", "192.168.56.11");
        String vm2Host = config("vm2.host", "VM2_HOST", "192.168.56.12");
        String vm3Host = config("vm3.host", "VM3_HOST", "192.168.56.13");
        String nacosStatus = enabled("spring.cloud.nacos.discovery.enabled", "NACOS_ENABLED", true)
                ? CONFIGURED : UNKNOWN;
        String minioStatus = enabled("resume.storage.enabled", "RESUME_OBJECT_STORAGE_ENABLED", true)
                ? CONFIGURED : OPTIONAL;
        String rocketMqStatus = enabled("delivery.events.rocketmq.enabled", "DELIVERY_EVENTS_ROCKETMQ_ENABLED", true)
                ? CONFIGURED : OPTIONAL;

        List<AdminDeploymentTopology.Node> nodes = List.of(
                new AdminDeploymentTopology.Node("vm1", "VM1 Edge Node", vm1Host, "EDGE",
                        List.of(
                                httpService("frontend", "Frontend", vm1Host,
                                        port("frontend.port", "FRONTEND_PORT", 80), "/", CONFIGURED,
                                        "Vue admin UI served by Nginx; /api is proxied to gateway-service."),
                                actuatorService("gateway-service", "Gateway", vm1Host,
                                        port("gateway.port", "GATEWAY_PORT", 8080), CONFIGURED,
                                        "Spring Cloud Gateway entrypoint for backend APIs."),
                                httpService("nacos", "Nacos", vm1Host,
                                        port("nacos.port", "NACOS_PORT", 8848), "/nacos", nacosStatus,
                                        "Standalone service discovery node for the three-VM deployment."))),
                new AdminDeploymentTopology.Node("vm2", "VM2 Business Services Node", vm2Host, "BUSINESS",
                        List.of(
                                actuatorService("auth-service", "Auth", vm2Host,
                                        port("auth.port", "AUTH_PORT", 8101), CONFIGURED,
                                        "Authentication service."),
                                actuatorService("user-service", "User Admin", vm2Host,
                                        port("user.port", "USER_PORT", 8102), CONFIGURED,
                                        "User and admin aggregation service."),
                                actuatorService("resume-service", "Resume", vm2Host,
                                        port("resume.port", "RESUME_PORT", 8103), CONFIGURED,
                                        "Resume upload, parsing, storage, and diagnosis service."),
                                actuatorService("job-service", "Job", vm2Host,
                                        port("job.port", "JOB_PORT", 8104), CONFIGURED,
                                        "Job publishing and AI-assisted job analysis service."),
                                actuatorService("match-service", "Match", vm2Host,
                                        port("match.port", "MATCH_PORT", 8105), CONFIGURED,
                                        "Resume-job matching service."),
                                actuatorService("delivery-service", "Delivery", vm2Host,
                                        port("delivery.port", "DELIVERY_PORT", 8107), CONFIGURED,
                                        "Job delivery workflow and event publishing service."))),
                new AdminDeploymentTopology.Node("vm3", "VM3 Data and AI Node", vm3Host, "DATA_AI",
                        List.of(
                                tcpService("mysql", "MySQL", vm3Host,
                                        port("mysql.port", "MYSQL_PORT", 3306), CONFIGURED,
                                        "Primary relational storage; credentials are intentionally omitted."),
                                tcpService("redis", "Redis", vm3Host,
                                        port("redis.port", "REDIS_PORT", 6379), CONFIGURED,
                                        "Cache-aside storage for service-level query results."),
                                httpService("minio", "MinIO", vm3Host,
                                        port("minio.port", "MINIO_PORT", 9000), "/minio/health/live", minioStatus,
                                        "Object storage for uploaded resume files."),
                                tcpService("rocketmq", "RocketMQ", vm3Host,
                                        port("rocketmq.port", "ROCKETMQ_PORT", 9876), rocketMqStatus,
                                        "Delivery event broker; broker ports are 10909 and 10911 by default."),
                                actuatorService("ai-service", "AI", vm3Host,
                                        port("ai.port", "AI_PORT", 8106), CONFIGURED,
                                        "DashScope-compatible AI orchestration service."))));

        return new AdminDeploymentTopology(Instant.now(), activeProfile(), activeProfile(), nodes, warnings());
    }

    private AdminDeploymentTopology.ServiceItem actuatorService(
            String name,
            String displayName,
            String host,
            int port,
            String status,
            String note) {
        return httpService(name, displayName, host, port, "/actuator/health", status, note);
    }

    private AdminDeploymentTopology.ServiceItem httpService(
            String name,
            String displayName,
            String host,
            int port,
            String path,
            String status,
            String note) {
        return new AdminDeploymentTopology.ServiceItem(name, displayName, port,
                "http://" + host + ":" + port + path, status, note);
    }

    private AdminDeploymentTopology.ServiceItem tcpService(
            String name,
            String displayName,
            String host,
            int port,
            String status,
            String note) {
        return new AdminDeploymentTopology.ServiceItem(name, displayName, port,
                "tcp://" + host + ":" + port, status, note);
    }

    private List<String> warnings() {
        String nacosStatus = enabled("spring.cloud.nacos.discovery.enabled", "NACOS_ENABLED", true)
                ? CONFIGURED : UNKNOWN;
        return List.of(
                "Topology is generated from deploy/docker-compose.vm1.yml, vm2.yml, vm3.yml, and environment overrides; it does not probe network health.",
                "Nacos discovery configuration status is " + nacosStatus + " for the current user-service process.");
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "default";
        }
        return String.join(",", profiles);
    }

    private String config(String canonicalName, String envName, String defaultValue) {
        String value = environment.getProperty(canonicalName);
        if (StringUtils.hasText(value)) {
            return value;
        }
        value = environment.getProperty(envName);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return defaultValue;
    }

    private int port(String canonicalName, String envName, int defaultPort) {
        return parsePort(config(canonicalName, envName, Integer.toString(defaultPort)), defaultPort);
    }

    private boolean enabled(String canonicalName, String envName, boolean defaultValue) {
        return Boolean.parseBoolean(config(canonicalName, envName, Boolean.toString(defaultValue)));
    }

    private int parsePort(String value, int defaultPort) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultPort;
        }
    }
}
