package com.aicampus.gateway.security;

import com.aicampus.common.enums.Role;
import com.aicampus.common.enums.Permission;
import com.aicampus.common.security.JwtTokenException;
import com.aicampus.common.security.JwtTokenService;
import com.aicampus.common.security.JwtTokenService.TokenClaims;
import com.aicampus.common.security.RolePermissionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayAuthFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/logout",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui");

    private final JwtTokenService jwtTokenService;
    private final boolean enabled;

    public JwtGatewayAuthFilter(
            @Value("${security.jwt.secret:${JWT_SECRET:}}") String jwtSecret,
            @Value("${security.jwt.issuer:${JWT_ISSUER:ai-campus-recruit}}") String jwtIssuer,
            @Value("${security.jwt.ttl-seconds:${JWT_TTL_SECONDS:86400}}") long jwtTtlSeconds,
            @Value("${security.gateway.auth.enabled:${GATEWAY_AUTH_ENABLED:true}}") boolean enabled) {
        this.enabled = enabled;
        this.jwtTokenService = enabled ? new JwtTokenService(jwtSecret, jwtIssuer, jwtTtlSeconds) : null;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled || isPublic(exchange.getRequest()) || !isProtectedApi(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        try {
            TokenClaims claims = jwtTokenService.verify(extractBearerToken(exchange.getRequest()));
            if (!isAllowed(path, exchange.getRequest().getMethod(), claims.role())) {
                return reject(exchange, HttpStatus.FORBIDDEN, "forbidden");
            }
            String permissions = String.join(",", RolePermissionPolicy.permissionNames(claims.role()));
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        headers.remove("X-User-Permissions");
                        headers.set("X-User-Id", claims.userId());
                        headers.set("X-User-Role", claims.role().name());
                        headers.set("X-User-Permissions", permissions);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtTokenException | IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "unauthorized");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(ServerHttpRequest request) {
        String path = request.getPath().pathWithinApplication().value();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isProtectedApi(ServerHttpRequest request) {
        String path = request.getPath().pathWithinApplication().value();
        return path.startsWith("/api/");
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            throw new JwtTokenException("Missing Authorization header");
        }
        String trimmed = authorization.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed.substring(7).trim() : trimmed;
    }

    private boolean isAllowed(String path, HttpMethod method, Role role) {
        Permission permission = requiredPermission(path, method);
        if (permission != null) {
            return RolePermissionPolicy.has(role, permission);
        }
        return RolePermissionPolicy.has(role, Permission.AUTH_SELF);
    }

    private Permission requiredPermission(String path, HttpMethod method) {
        if (path.startsWith("/api/auth/admin") || path.startsWith("/api/admin/accounts")) {
            return method == HttpMethod.GET ? Permission.ACCOUNT_READ : Permission.ACCOUNT_WRITE;
        }
        if (path.startsWith("/api/accounts/") && path.endsWith("/password")) {
            return Permission.AUTH_SELF;
        }
        if (path.startsWith("/api/auth/me")
                || path.startsWith("/api/auth/permissions")
                || path.startsWith("/api/auth/password/change")) {
            return Permission.AUTH_SELF;
        }
        if (path.startsWith("/api/admin/audit")) {
            return method == HttpMethod.POST ? Permission.ADMIN_AUDIT_EXPORT : Permission.ADMIN_AUDIT_READ;
        }
        if (path.startsWith("/api/admin")) {
            return path.startsWith("/api/admin/system") ? Permission.SYSTEM_VIEW : Permission.ADMIN_DASHBOARD;
        }
        if (path.startsWith("/api/students") || path.startsWith("/api/resumes") || path.startsWith("/api/matches")) {
            if (path.startsWith("/api/students")) {
                return Permission.STUDENT_PROFILE;
            }
            if (path.startsWith("/api/resumes")) {
                return Permission.STUDENT_RESUME_WRITE;
            }
            return Permission.MATCH_RUN;
        }
        if (path.startsWith("/api/deliveries")) {
            return deliveryPermission(path, method);
        }
        if (path.startsWith("/api/jobs")) {
            return method == HttpMethod.GET ? Permission.JOB_READ : Permission.COMPANY_JOB_WRITE;
        }
        if (path.startsWith("/api/ai")) {
            return aiPermission(path);
        }
        return null;
    }

    private Permission deliveryPermission(String path, HttpMethod method) {
        if (path.startsWith("/api/deliveries/company") || method == HttpMethod.PUT) {
            return Permission.COMPANY_DELIVERY_READ;
        }
        if (path.startsWith("/api/deliveries/my") || method == HttpMethod.POST) {
            return Permission.STUDENT_DELIVERY_WRITE;
        }
        return Permission.AUTH_SELF;
    }

    private Permission aiPermission(String path) {
        if (path.startsWith("/api/ai/observability")) {
            return Permission.AI_OBSERVABILITY_READ;
        }
        if (path.startsWith("/api/ai/candidates")) {
            return Permission.COMPANY_SCREENING_WRITE;
        }
        if (path.startsWith("/api/ai/interview")) {
            return Permission.STUDENT_INTERVIEW_WRITE;
        }
        if (path.startsWith("/api/ai/status")) {
            return Permission.AUTH_SELF;
        }
        return Permission.AI_ANALYZE;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status.value() + ",\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
