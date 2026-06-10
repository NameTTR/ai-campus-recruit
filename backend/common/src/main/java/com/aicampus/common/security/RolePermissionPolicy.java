package com.aicampus.common.security;

import com.aicampus.common.enums.Permission;
import com.aicampus.common.enums.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RolePermissionPolicy {
    private static final Map<Role, Set<Permission>> PERMISSIONS = buildPermissions();

    private RolePermissionPolicy() {
    }

    public static boolean has(Role role, Permission permission) {
        return role != null && permission != null && PERMISSIONS.getOrDefault(role, Set.of()).contains(permission);
    }

    public static List<String> permissionNames(Role role) {
        return permissions(role).stream()
                .filter(Permission::exposed)
                .map(Permission::code)
                .toList();
    }

    public static Set<Permission> permissions(Role role) {
        return EnumSet.copyOf(PERMISSIONS.getOrDefault(role, Set.of(Permission.AUTH_SELF)));
    }

    private static Map<Role, Set<Permission>> buildPermissions() {
        EnumMap<Role, Set<Permission>> permissions = new EnumMap<>(Role.class);
        permissions.put(Role.STUDENT, EnumSet.of(
                Permission.AUTH_SELF,
                Permission.STUDENT_PROFILE,
                Permission.STUDENT_RESUME_WRITE,
                Permission.JOB_READ,
                Permission.MATCH_RUN,
                Permission.STUDENT_DELIVERY_WRITE,
                Permission.AI_ANALYZE,
                Permission.STUDENT_INTERVIEW_WRITE));
        permissions.put(Role.COMPANY, EnumSet.of(
                Permission.AUTH_SELF,
                Permission.JOB_READ,
                Permission.COMPANY_JOB_WRITE,
                Permission.COMPANY_DELIVERY_READ,
                Permission.AI_ANALYZE,
                Permission.COMPANY_SCREENING_WRITE));
        permissions.put(Role.ADMIN, EnumSet.allOf(Permission.class));
        return Map.copyOf(permissions);
    }
}
