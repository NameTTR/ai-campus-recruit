package com.aicampus.common.dto;

import com.aicampus.common.enums.Role;
import java.util.List;

public record UserProfile(
        String userId,
        String displayName,
        Role role,
        String school,
        String major,
        List<String> skills,
        String targetPosition
) {
}

