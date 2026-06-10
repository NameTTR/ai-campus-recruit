package com.aicampus.common.dto;

import com.aicampus.common.enums.AccountStatus;
import com.aicampus.common.enums.Role;

public record AccountCreateRequest(
        String username,
        String password,
        String displayName,
        Role role,
        AccountStatus status) {
}
