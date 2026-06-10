package com.aicampus.common.dto;

import com.aicampus.common.enums.AccountStatus;
import com.aicampus.common.enums.Role;
import java.time.Instant;
import java.util.List;

public record AccountSummary(
        String accountId,
        String username,
        String displayName,
        Role role,
        AccountStatus status,
        List<String> permissions,
        Instant createdAt,
        Instant updatedAt) {
}
