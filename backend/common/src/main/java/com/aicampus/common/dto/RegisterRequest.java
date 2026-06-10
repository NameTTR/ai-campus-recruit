package com.aicampus.common.dto;

import com.aicampus.common.enums.Role;

public record RegisterRequest(String username, String password, String displayName, Role role) {
}
