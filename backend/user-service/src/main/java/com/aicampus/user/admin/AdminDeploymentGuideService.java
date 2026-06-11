package com.aicampus.user.admin;

import java.time.Instant;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminDeploymentGuideService {
    private final Environment environment;

    public AdminDeploymentGuideService(Environment environment) {
        this.environment = environment;
    }

    public AdminDeploymentGuide guide() {
        DeploymentConfig config = deploymentConfig();
        return new AdminDeploymentGuide(
                Instant.now(),
                activeProfile(),
                "Generated from deploy/three-vm.env.example and the three docker-compose VM files; no live network probes are performed.",
                steps(config),
                acceptanceChecks(config),
                warnings());
    }

    private List<AdminDeploymentGuide.Step> steps(DeploymentConfig config) {
        return List.of(
                new AdminDeploymentGuide.Step(
                        1,
                        "vm1",
                        "VM1 Nacos Bootstrap",
                        "Start service discovery",
                        "Start Nacos first so VM2 business services and VM3 ai-service can register without startup races.",
                        List.of(
                                "cd /opt/ai-campus-recruit",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d nacos",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml ps nacos"),
                        List.of(httpUrl(config.vm1Host(), config.nacosPort(), "/nacos/")),
                        "Nacos is running and the console endpoint is reachable before dependent services start.",
                        List.of(
                                "If Nacos is unavailable, check VM1 firewall rules for ports 8848 and 9848.",
                                "If dependent services cannot register, confirm VM2 and VM3 can reach VM1 on the Nacos ports.")),
                new AdminDeploymentGuide.Step(
                        2,
                        "vm3",
                        "VM3 Data and AI Node",
                        "Start infrastructure and AI service",
                        "Bring up MySQL, Redis, MinIO, RocketMQ, and ai-service after Nacos is ready.",
                        List.of(
                                "cd /opt/ai-campus-recruit",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d --build",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml ps"),
                        List.of(
                                tcpUrl(config.vm3Host(), config.mysqlPort()),
                                tcpUrl(config.vm3Host(), config.redisPort()),
                                httpUrl(config.vm3Host(), config.minioPort(), "/minio/health/live"),
                                tcpUrl(config.vm3Host(), config.rocketMqPort()),
                                httpUrl(config.vm3Host(), config.aiPort(), "/actuator/health")),
                        "MySQL, Redis, MinIO, RocketMQ, and ai-service containers are running; ai-service health returns UP or a normal Spring health payload.",
                        List.of(
                                "If ai-service cannot start, check that VM1 Nacos is reachable and restart ai-service.",
                                "If storage services are unhealthy, inspect the VM3 compose logs and confirm data volumes were created.",
                                "If the AI provider is not configured, the platform can still use offline demo AI responses.")),
                new AdminDeploymentGuide.Step(
                        3,
                        "vm2",
                        "VM2 Business Services Node",
                        "Start business microservices",
                        "Start auth, user, resume, job, match, and delivery services after their dependencies are available.",
                        List.of(
                                "cd /opt/ai-campus-recruit",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml up -d --build",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml ps"),
                        List.of(
                                httpUrl(config.vm2Host(), config.authPort(), "/actuator/health"),
                                httpUrl(config.vm2Host(), config.userPort(), "/actuator/health"),
                                httpUrl(config.vm2Host(), config.resumePort(), "/actuator/health"),
                                httpUrl(config.vm2Host(), config.jobPort(), "/actuator/health"),
                                httpUrl(config.vm2Host(), config.matchPort(), "/actuator/health"),
                                httpUrl(config.vm2Host(), config.deliveryPort(), "/actuator/health")),
                        "All business services are running and can register with Nacos or be reached by direct gateway routes.",
                        List.of(
                                "If a business service fails to start, inspect its datasource, Redis, and Nacos connection logs.",
                                "If resume upload fails, verify MinIO on VM3 is reachable from VM2.",
                                "If delivery events fail, verify RocketMQ name server connectivity from delivery-service.")),
                new AdminDeploymentGuide.Step(
                        4,
                        "vm1",
                        "VM1 Gateway and Frontend",
                        "Start edge access",
                        "Start gateway-service and frontend after VM2 and VM3 services are available.",
                        List.of(
                                "cd /opt/ai-campus-recruit",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d --build gateway-service frontend",
                                "docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml ps"),
                        List.of(
                                httpUrl(config.vm1Host(), config.gatewayPort(), "/actuator/health"),
                                httpUrl(config.vm1Host(), config.frontendPort(), "/")),
                        "Gateway health and the frontend entry page are reachable from the host machine.",
                        List.of(
                                "If gateway routing fails, confirm VM2 and VM3 host addresses in deploy/three-vm.env.",
                                "If frontend loads but APIs fail, confirm the gateway container can reach VM2 service ports.")),
                new AdminDeploymentGuide.Step(
                        5,
                        "all",
                        "All Nodes",
                        "Run health checks and API smoke",
                        "Confirm the distributed deployment is usable through the gateway and the admin console.",
                        List.of(
                                ".\\scripts\\check-three-vm-health.ps1 -EnvFile .\\deploy\\three-vm.env -TimeoutSeconds 5",
                                ".\\scripts\\check-api-smoke.ps1 -BaseUrl "
                                        + httpUrl(config.vm1Host(), config.gatewayPort(), "") + " -TimeoutSeconds 8",
                                "bash scripts/check-three-vm-health.sh --env-file deploy/three-vm.env --timeout 5",
                                "bash scripts/check-api-smoke.sh --base-url "
                                        + httpUrl(config.vm1Host(), config.gatewayPort(), "") + " --timeout 8"),
                        List.of(
                                httpUrl(config.vm1Host(), config.gatewayPort(), "/actuator/health"),
                                httpUrl(config.vm1Host(), config.gatewayPort(), "/api/admin/system/status"),
                                httpUrl(config.vm1Host(), config.gatewayPort(), "/api/admin/system/topology"),
                                httpUrl(config.vm1Host(), config.gatewayPort(), "/api/admin/system/deployment-guide"),
                                httpUrl(config.vm1Host(), config.frontendPort(), "/")),
                        "Gateway health and admin system APIs return successful JSON responses, and the frontend opens normally.",
                        List.of(
                                "If gateway smoke tests fail, check gateway routes and service registration status.",
                                "If only frontend fails, inspect the frontend container logs and gateway upstream setting.",
                                "If one service is missing, restart that service after confirming its VM host and port mapping.")));
    }

    private List<AdminDeploymentGuide.AcceptanceCheck> acceptanceChecks(DeploymentConfig config) {
        return List.of(
                new AdminDeploymentGuide.AcceptanceCheck(
                        "Three-VM health check",
                        ".\\scripts\\check-three-vm-health.ps1 -EnvFile .\\deploy\\three-vm.env -TimeoutSeconds 5",
                        "Frontend, gateway, Nacos, business services, AI service, and infrastructure checks pass."),
                new AdminDeploymentGuide.AcceptanceCheck(
                        "Linux health check",
                        "bash scripts/check-three-vm-health.sh --env-file deploy/three-vm.env --timeout 5",
                        "The bash health check exits successfully on a machine that can reach all three VMs."),
                new AdminDeploymentGuide.AcceptanceCheck(
                        "API smoke",
                        ".\\scripts\\check-api-smoke.ps1 -BaseUrl "
                                + httpUrl(config.vm1Host(), config.gatewayPort(), "") + " -TimeoutSeconds 8",
                        "Login, resume, job, match, delivery, AI, and admin APIs return successful responses."),
                new AdminDeploymentGuide.AcceptanceCheck(
                        "Admin guide endpoint",
                        "curl " + httpUrl(config.vm1Host(), config.gatewayPort(), "/api/admin/system/deployment-guide"),
                        "The response code is 0 and the steps array contains VM1 Nacos, VM3, VM2, VM1 edge, then all-nodes checks."));
    }

    private List<String> warnings() {
        return List.of(
                "This guide is generated from configuration defaults and environment overrides; it does not probe runtime network health.",
                "Start VM1 Nacos before VM3 ai-service and VM2 business services to avoid registration startup races.",
                "Keep credential values only in the env file or host environment; this API intentionally omits them.");
    }

    private DeploymentConfig deploymentConfig() {
        return new DeploymentConfig(
                config("vm1.host", "VM1_HOST", "192.168.56.11"),
                config("vm2.host", "VM2_HOST", "192.168.56.12"),
                config("vm3.host", "VM3_HOST", "192.168.56.13"),
                port("frontend.port", "FRONTEND_PORT", 80),
                port("gateway.port", "GATEWAY_PORT", 8080),
                port("nacos.port", "NACOS_PORT", 8848),
                port("auth.port", "AUTH_PORT", 8101),
                port("user.port", "USER_PORT", 8102),
                port("resume.port", "RESUME_PORT", 8103),
                port("job.port", "JOB_PORT", 8104),
                port("match.port", "MATCH_PORT", 8105),
                port("ai.port", "AI_PORT", 8106),
                port("delivery.port", "DELIVERY_PORT", 8107),
                port("mysql.port", "MYSQL_PORT", 3306),
                port("redis.port", "REDIS_PORT", 6379),
                port("minio.port", "MINIO_PORT", 9000),
                port("rocketmq.port", "ROCKETMQ_PORT", 9876));
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

    private int parsePort(String value, int defaultPort) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultPort;
        }
    }

    private String httpUrl(String host, int port, String path) {
        return "http://" + host + ":" + port + path;
    }

    private String tcpUrl(String host, int port) {
        return "tcp://" + host + ":" + port;
    }

    private record DeploymentConfig(
            String vm1Host,
            String vm2Host,
            String vm3Host,
            int frontendPort,
            int gatewayPort,
            int nacosPort,
            int authPort,
            int userPort,
            int resumePort,
            int jobPort,
            int matchPort,
            int aiPort,
            int deliveryPort,
            int mysqlPort,
            int redisPort,
            int minioPort,
            int rocketMqPort) {
    }
}
