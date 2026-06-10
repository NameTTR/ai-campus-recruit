package com.aicampus.common.enums;

public enum Permission {
    AUTH_SELF("auth:self", false),
    ACCOUNT_READ("admin:account:read", true),
    ACCOUNT_WRITE("admin:account:write", true),
    ADMIN_DASHBOARD("admin:dashboard:read", true),
    ADMIN_RBAC_READ("admin:rbac:read", true),
    STUDENT_PROFILE("student:profile:read", true),
    STUDENT_RESUME_WRITE("student:resume:write", true),
    STUDENT_DELIVERY_WRITE("student:delivery:write", true),
    STUDENT_INTERVIEW_WRITE("student:interview:write", true),
    COMPANY_JOB_WRITE("company:job:write", true),
    COMPANY_DELIVERY_READ("company:delivery:read", true),
    COMPANY_SCREENING_WRITE("company:screening:write", true),
    JOB_READ("job:read", false),
    MATCH_RUN("match:run", false),
    AI_ANALYZE("ai:analyze", false),
    SYSTEM_VIEW("system:view", false);

    private final String code;
    private final boolean exposed;

    Permission(String code, boolean exposed) {
        this.code = code;
        this.exposed = exposed;
    }

    public String code() {
        return code;
    }

    public boolean exposed() {
        return exposed;
    }
}
