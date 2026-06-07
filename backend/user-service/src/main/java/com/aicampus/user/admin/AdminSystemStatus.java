package com.aicampus.user.admin;

import java.time.Instant;
import java.util.List;

public record AdminSystemStatus(
        Instant generatedAt,
        String applicationName,
        String profile,
        String environment,
        List<ServiceStatus> services,
        List<PersistenceItem> persistence,
        List<InfrastructureItem> infrastructure,
        List<String> warnings) {

    public record ServiceStatus(
            String name,
            String displayName,
            int defaultPort,
            int port,
            String healthPath,
            String status,
            String note) {
    }

    public record PersistenceItem(
            String module,
            boolean enabled,
            String database,
            String cacheKeyPrefix,
            String note,
            String notes) {
    }

    public record InfrastructureItem(
            String name,
            String host,
            int port,
            boolean configured,
            String status,
            String note) {
    }
}
