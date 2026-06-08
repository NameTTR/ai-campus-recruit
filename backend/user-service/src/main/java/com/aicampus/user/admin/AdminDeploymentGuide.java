package com.aicampus.user.admin;

import java.time.Instant;
import java.util.List;

public record AdminDeploymentGuide(
        Instant generatedAt,
        String environment,
        String summary,
        List<Step> steps,
        List<AcceptanceCheck> acceptanceChecks,
        List<String> warnings) {

    public record Step(
            int order,
            String nodeId,
            String nodeName,
            String title,
            String purpose,
            List<String> commands,
            List<String> verifyUrls,
            String expectedResult,
            List<String> troubleshooting) {
    }

    public record AcceptanceCheck(
            String name,
            String command,
            String expectedResult) {
    }
}
