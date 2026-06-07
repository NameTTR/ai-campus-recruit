package com.aicampus.user.admin;

import java.time.Instant;
import java.util.List;

public record AdminDeploymentTopology(
        Instant generatedAt,
        String profile,
        String environment,
        List<Node> nodes,
        List<String> warnings) {

    public record Node(
            String id,
            String name,
            String host,
            String role,
            List<ServiceItem> services) {
    }

    public record ServiceItem(
            String name,
            String displayName,
            int port,
            String healthUrl,
            String status,
            String note) {
    }
}
