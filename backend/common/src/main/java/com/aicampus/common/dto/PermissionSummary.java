package com.aicampus.common.dto;

import com.aicampus.common.enums.Role;
import java.util.List;

public record PermissionSummary(String userId, Role role, List<String> permissions) {
}
