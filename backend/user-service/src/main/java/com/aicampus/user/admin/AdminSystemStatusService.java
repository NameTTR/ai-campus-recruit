package com.aicampus.user.admin;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminSystemStatusService {
    private static final String CONFIGURED = "CONFIGURED";
    private static final String DISABLED = "DISABLED";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String HEALTH_PATH = "/actuator/health";

    private final Environment environment;

    public AdminSystemStatusService(Environment environment) {
        this.environment = environment;
    }

    public AdminSystemStatus status() {
        String applicationName = config("spring.application.name", "SPRING_APPLICATION_NAME", "user-service");
        String profile = activeProfile();
        String database = databaseName();

        boolean resumePersistence = enabled("resume.persistence.enabled", "RESUME_PERSISTENCE_ENABLED", false);
        boolean jobPersistence = enabled("job.persistence.enabled", "JOB_PERSISTENCE_ENABLED", false);
        boolean matchPersistence = enabled("match.persistence.enabled", "MATCH_PERSISTENCE_ENABLED", false);
        boolean deliveryPersistence = enabled("delivery.persistence.enabled", "DELIVERY_PERSISTENCE_ENABLED", false);
        boolean aiScreeningPersistence = enabled("ai.screening.persistence.enabled",
                "AI_SCREENING_PERSISTENCE_ENABLED", false);

        List<AdminSystemStatus.PersistenceItem> persistence = List.of(
                persistenceItem("resume", resumePersistence, database,
                        config("resume.cache.key-prefix", "RESUME_CACHE_KEY_PREFIX", "resume:summaries")),
                persistenceItem("job", jobPersistence, database,
                        config("job.cache.key-prefix", "JOB_CACHE_KEY_PREFIX", "job:records")),
                persistenceItem("match", matchPersistence, database,
                        config("match.cache.key-prefix", "MATCH_CACHE_KEY_PREFIX", "match:results")),
                persistenceItem("delivery", deliveryPersistence, database,
                        config("delivery.cache.key-prefix", "DELIVERY_CACHE_KEY_PREFIX", "delivery:records")),
                persistenceItem("aiScreening", aiScreeningPersistence, database,
                        config("ai.screening.cache.key-prefix", "AI_SCREENING_CACHE_KEY_PREFIX",
                                "ai:screening:records")));

        List<AdminSystemStatus.InfrastructureItem> infrastructure = infrastructure();
        List<String> warnings = warnings(List.of(
                new PersistenceFlag("resume", resumePersistence),
                new PersistenceFlag("job", jobPersistence),
                new PersistenceFlag("match", matchPersistence),
                new PersistenceFlag("delivery", deliveryPersistence),
                new PersistenceFlag("aiScreening", aiScreeningPersistence)));

        return new AdminSystemStatus(Instant.now(), applicationName, profile, profile, services(), persistence,
                infrastructure, warnings);
    }

    private List<AdminSystemStatus.ServiceStatus> services() {
        return List.of(
                service("gateway-service", "Gateway", 8080, "GATEWAY_SERVICE_URI", false),
                service("auth-service", "Auth", 8101, "AUTH_SERVICE_URI", false),
                service("user-service", "User Admin", 8102, "USER_SERVICE_URI", true),
                service("resume-service", "Resume", 8103, "RESUME_SERVICE_URI", false),
                service("job-service", "Job", 8104, "JOB_SERVICE_URI", false),
                service("match-service", "Match", 8105, "MATCH_SERVICE_URI", false),
                service("ai-service", "AI", 8106, "AI_SERVICE_URI", false),
                service("delivery-service", "Delivery", 8107, "DELIVERY_SERVICE_URI", false));
    }

    private AdminSystemStatus.ServiceStatus service(
            String name,
            String displayName,
            int defaultPort,
            String uriEnvName,
            boolean current) {
        String serviceUri = environment.getProperty(uriEnvName);
        boolean configured = StringUtils.hasText(serviceUri);
        Endpoint endpoint = configured ? parseEndpoint(serviceUri, name, defaultPort) : new Endpoint(name, defaultPort);
        String status = current || configured ? CONFIGURED : UNKNOWN;
        String note = current ? "Current application context." : "Status is not network-probed by user-service.";
        return new AdminSystemStatus.ServiceStatus(name, displayName, defaultPort, endpoint.port(), HEALTH_PATH, status,
                note);
    }

    private AdminSystemStatus.PersistenceItem persistenceItem(
            String name,
            boolean enabled,
            String database,
            String cacheKeyPrefix) {
        String notes = enabled
                ? name + " persistence is enabled when its service receives the datasource configuration."
                : name + " uses in-memory demo storage until persistence is enabled.";
        return new AdminSystemStatus.PersistenceItem(name, enabled, database, cacheKeyPrefix, notes, notes);
    }

    private List<AdminSystemStatus.InfrastructureItem> infrastructure() {
        boolean nacosEnabled = enabled("spring.cloud.nacos.discovery.enabled", "NACOS_ENABLED", false);
        Endpoint nacos = parseEndpoint(config("spring.cloud.nacos.discovery.server-addr", "NACOS_SERVER_ADDR",
                "127.0.0.1:8848"), "127.0.0.1", 8848);

        String jdbcUrl = config("spring.datasource.url", "SPRING_DATASOURCE_URL", "");
        Endpoint mysql = parseJdbcMysql(jdbcUrl);
        boolean mysqlConfigured = StringUtils.hasText(jdbcUrl);

        String redisHost = config("spring.data.redis.host", "SPRING_DATA_REDIS_HOST", "127.0.0.1");
        int redisPort = parsePort(config("spring.data.redis.port", "SPRING_DATA_REDIS_PORT", "6379"), 6379);
        boolean redisConfigured = explicit("spring.data.redis.host", "SPRING_DATA_REDIS_HOST");

        Endpoint minio = parseEndpoint(config("resume.storage.endpoint", "MINIO_ENDPOINT",
                "http://localhost:9000"), "localhost", 9000);
        boolean minioEnabled = enabled("resume.storage.enabled", "RESUME_OBJECT_STORAGE_ENABLED", false);
        boolean minioConfigured = minioEnabled || explicit("resume.storage.endpoint", "MINIO_ENDPOINT");

        Endpoint rocketmq = parseEndpoint(config("delivery.events.rocketmq.name-server", "ROCKETMQ_NAME_SERVER",
                "127.0.0.1:9876"), "127.0.0.1", 9876);
        boolean rocketmqEnabled = enabled("delivery.events.rocketmq.enabled",
                "DELIVERY_EVENTS_ROCKETMQ_ENABLED", false);
        boolean rocketmqConfigured = rocketmqEnabled
                || explicit("delivery.events.rocketmq.name-server", "ROCKETMQ_NAME_SERVER");

        return List.of(
                new AdminSystemStatus.InfrastructureItem("nacos", nacos.host(), nacos.port(), nacosEnabled,
                        nacosEnabled ? CONFIGURED : DISABLED, "Service discovery is optional in local demo mode."),
                new AdminSystemStatus.InfrastructureItem("mysql", mysql.host(), mysql.port(), mysqlConfigured,
                        mysqlConfigured ? CONFIGURED : UNKNOWN, "Datasource URL is summarized without credentials."),
                new AdminSystemStatus.InfrastructureItem("redis", redisHost, redisPort, redisConfigured,
                        redisConfigured ? CONFIGURED : UNKNOWN, "Cache-aside modules use service-level Redis config."),
                new AdminSystemStatus.InfrastructureItem("minio", minio.host(), minio.port(), minioConfigured,
                        minioEnabled ? CONFIGURED : DISABLED, "Resume object storage is optional."),
                new AdminSystemStatus.InfrastructureItem("rocketmq", rocketmq.host(), rocketmq.port(),
                        rocketmqConfigured, rocketmqEnabled ? CONFIGURED : DISABLED,
                        "Delivery event publishing is optional."));
    }

    private List<String> warnings(List<PersistenceFlag> persistenceFlags) {
        List<String> warnings = new ArrayList<>();
        if (!StringUtils.hasText(config("dashscope.api-key", "DASHSCOPE_API_KEY", ""))) {
            warnings.add("AI key is not configured; ai-service will use offline demo responses.");
        }

        List<String> disabledPersistence = persistenceFlags.stream()
                .filter(flag -> !flag.enabled())
                .map(PersistenceFlag::name)
                .toList();
        if (!disabledPersistence.isEmpty()) {
            warnings.add("Persistence is disabled for " + String.join("/", disabledPersistence)
                    + "; those services use in-memory demo storage by default.");
        }

        boolean anyPersistenceEnabled = persistenceFlags.stream().anyMatch(PersistenceFlag::enabled);
        if (anyPersistenceEnabled && !StringUtils.hasText(config("spring.datasource.url", "SPRING_DATASOURCE_URL", ""))) {
            warnings.add("Persistence is enabled for at least one module, but datasource URL is not configured.");
        }
        if (anyPersistenceEnabled && !explicit("spring.data.redis.host", "SPRING_DATA_REDIS_HOST")) {
            warnings.add("Redis host is not explicitly configured; cache-aside modules will rely on service defaults.");
        }
        if (!enabled("spring.cloud.nacos.discovery.enabled", "NACOS_ENABLED", false)) {
            warnings.add("Nacos discovery is disabled; local demo routing should use direct service URIs.");
        }
        return warnings;
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "default";
        }
        return String.join(",", profiles);
    }

    private String databaseName() {
        String jdbcUrl = config("spring.datasource.url", "SPRING_DATASOURCE_URL", "");
        if (StringUtils.hasText(jdbcUrl)) {
            String database = parseJdbcDatabase(jdbcUrl);
            if (StringUtils.hasText(database)) {
                return database;
            }
        }
        return config("mysql.database", "MYSQL_DATABASE", "ai_campus_recruit");
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

    private boolean enabled(String canonicalName, String envName, boolean defaultValue) {
        return Boolean.parseBoolean(config(canonicalName, envName, Boolean.toString(defaultValue)));
    }

    private boolean explicit(String... names) {
        return Arrays.stream(names)
                .anyMatch(name -> environment.containsProperty(name)
                        && StringUtils.hasText(environment.getProperty(name)));
    }

    private Endpoint parseJdbcMysql(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return new Endpoint("127.0.0.1", 3306);
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            return new Endpoint(blankToDefault(uri.getHost(), "127.0.0.1"),
                    uri.getPort() > 0 ? uri.getPort() : 3306);
        } catch (RuntimeException ex) {
            return new Endpoint("127.0.0.1", 3306);
        }
    }

    private String parseJdbcDatabase(String jdbcUrl) {
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || "/".equals(path)) {
                return "";
            }
            return path.substring(1);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private Endpoint parseEndpoint(String rawValue, String defaultHost, int defaultPort) {
        if (!StringUtils.hasText(rawValue)) {
            return new Endpoint(defaultHost, defaultPort);
        }
        String first = rawValue.split(",")[0].trim();
        try {
            if (first.contains("://")) {
                URI uri = URI.create(first);
                return new Endpoint(blankToDefault(uri.getHost(), defaultHost),
                        uri.getPort() > 0 ? uri.getPort() : defaultPort);
            }
            String hostPort = first.split("/", 2)[0];
            int portSeparator = hostPort.lastIndexOf(':');
            if (portSeparator > 0 && portSeparator < hostPort.length() - 1) {
                return new Endpoint(hostPort.substring(0, portSeparator),
                        parsePort(hostPort.substring(portSeparator + 1), defaultPort));
            }
            return new Endpoint(hostPort, defaultPort);
        } catch (RuntimeException ex) {
            return new Endpoint(defaultHost, defaultPort);
        }
    }

    private int parsePort(String value, int defaultPort) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultPort;
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private record Endpoint(String host, int port) {
    }

    private record PersistenceFlag(String name, boolean enabled) {
    }
}
