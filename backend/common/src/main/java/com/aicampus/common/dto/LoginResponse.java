package com.aicampus.common.dto;

import com.aicampus.common.enums.Role;

public record LoginResponse(String token, String userId, String displayName, Role role) {
}

