package com.aicampus.gateway.security;

import com.aicampus.common.enums.Permission;
import com.aicampus.common.enums.Role;
import com.aicampus.common.security.JwtTokenException;
import com.aicampus.common.security.JwtTokenService.TokenClaims;
import com.aicampus.common.security.JwtTokenService;
import com.aicampus.common.security.RolePermissionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayAuthFilter implements WebFilter, Ordered {
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/favicon.ico");
    private static final List<String> DOCUMENTATION_PREFIXES = List.of(
            "/v3/api-docs",
            "/swagger-ui",
            "/doc.html",
            "/webjars",
            "/swagger-resources");

    private final JwtTokenService jwtTokenService;
    private final boolean enabled;
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    /**
     * API documentation is convenient in development, but should be private in
     * production.  Keep the default enabled for backwards-compatible local use;
     * set GATEWAY_PUBLIC_DOCS_ENABLED=false when exposing the gateway publicly.
     */
    @Value("${security.gateway.public-docs-enabled:${GATEWAY_PUBLIC_DOCS_ENABLED:true}}")
    private boolean publicDocumentationEnabled = true;

    public JwtGatewayAuthFilter(
            @Value("${security.jwt.secret:${JWT_SECRET:}}") String jwtSecret,
            @Value("${security.jwt.issuer:${JWT_ISSUER:ai-campus-recruit}}") String jwtIssuer,
            @Value("${security.jwt.ttl-seconds:${JWT_TTL_SECONDS:86400}}") long jwtTtlSeconds,
            @Value("${security.gateway.auth.enabled:${GATEWAY_AUTH_ENABLED:true}}") boolean enabled) {
        this.enabled = enabled;
        this.jwtTokenService = enabled ? new JwtTokenService(jwtSecret, jwtIssuer, jwtTtlSeconds) : null;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Identity headers are gateway-owned.  Strip client-supplied values even
        // on public/non-API routes so they cannot be trusted by downstream code.
        ServerHttpRequest sanitizedRequest = stripIdentityHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();
        if (enabled && isLogoutPath(sanitizedRequest)) {
            return revokeLogoutToken(sanitizedRequest, sanitizedExchange, chain);
        }
        if (!enabled || isPublic(sanitizedRequest)
                || (!isProtectedApi(sanitizedRequest)
                && !isDocumentationPath(sanitizedRequest)
                && !isProtectedManagementPath(sanitizedRequest))) {
            return chain.filter(sanitizedExchange);
        }
        String path = requestPath(sanitizedRequest);
        try {
            String token = extractBearerToken(sanitizedRequest);
            if (isRevoked(token)) {
                return reject(sanitizedExchange, HttpStatus.UNAUTHORIZED, "unauthorized");
            }
            TokenClaims claims = jwtTokenService.verify(token);
            if (!isAllowed(path, sanitizedRequest.getMethod(), claims.role())) {
                return reject(sanitizedExchange, HttpStatus.FORBIDDEN, "forbidden");
            }
            String permissions = String.join(",", RolePermissionPolicy.permissionNames(claims.role()));
            ServerHttpRequest mutatedRequest = sanitizedRequest.mutate()
                    .headers(headers -> {
                        headers.set("X-User-Id", claims.userId());
                        headers.set("X-User-Role", claims.role().name());
                        headers.set("X-User-Permissions", permissions);
                    })
                    .build();
            return chain.filter(sanitizedExchange.mutate().request(mutatedRequest).build());
        } catch (JwtTokenException | IllegalArgumentException ex) {
            return reject(sanitizedExchange, HttpStatus.UNAUTHORIZED, "unauthorized");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Mono<Void> revokeLogoutToken(ServerHttpRequest request, ServerWebExchange exchange, WebFilterChain chain) {
        try {
            String token = extractBearerToken(request);
            TokenClaims claims = jwtTokenService.verify(token);
            revokedTokens.put(token, claims.expiresAt());
            return chain.filter(exchange);
        } catch (JwtTokenException | IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "unauthorized");
        }
    }

    private boolean isRevoked(String token) {
        long now = java.time.Instant.now().getEpochSecond();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
        Long expiresAt = revokedTokens.get(token);
        return expiresAt != null && expiresAt > now;
    }

    private String requestPath(ServerHttpRequest request) {
        // Match the decoded path segments used by Spring's route/controller
        // matching, including matrix parameters, before making authorization decisions.
        return request.getPath().pathWithinApplication().elements().stream()
                .map(element -> element instanceof PathContainer.PathSegment segment
                        ? segment.valueToMatch() : element.value())
                .collect(Collectors.joining());
    }
    private boolean isLogoutPath(ServerHttpRequest request) {
        return request.getMethod() == HttpMethod.POST
                && "/api/auth/logout".equals(requestPath(request));
    }

    private boolean isPublic(ServerHttpRequest request) {
        String path = requestPath(request);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return true;
        }
        if (isHealthPath(path)) {
            return true;
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        return publicDocumentationEnabled
                && DOCUMENTATION_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) {
        return request.mutate().headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-User-Role");
            headers.remove("X-User-Permissions");
        }).build();
    }

    private boolean isProtectedApi(ServerHttpRequest request) {
        String path = requestPath(request);
        return path.startsWith("/api/");
    }

    private boolean isDocumentationPath(ServerHttpRequest request) {
        String path = requestPath(request);
        return DOCUMENTATION_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isProtectedManagementPath(ServerHttpRequest request) {
        String path = requestPath(request);
        return ("/actuator".equals(path) || path.startsWith("/actuator/")) && !isHealthPath(path);
    }

    private boolean isHealthPath(String path) {
        return "/actuator/health".equals(path) || path.startsWith("/actuator/health/");
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
        if ("/actuator".equals(path) || path.startsWith("/actuator/")) {
            return Permission.SYSTEM_VIEW;
        }
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
        if (path.startsWith("/api/notifications")) {
            return notificationPermission(path, method);
        }
        if (path.startsWith("/api/interviews/schedules")) {
            return interviewSchedulePermission(path, method);
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

    private Permission notificationPermission(String path, HttpMethod method) {
        if (path.startsWith("/api/notifications/company")) {
            return Permission.COMPANY_DELIVERY_READ;
        }
        if (path.startsWith("/api/notifications/my")) {
            return Permission.STUDENT_PROFILE;
        }
        return Permission.AUTH_SELF;
    }

    private Permission interviewSchedulePermission(String path, HttpMethod method) {
        if (path.startsWith("/api/interviews/schedules/company") || method == HttpMethod.POST) {
            return Permission.COMPANY_DELIVERY_READ;
        }
        if (path.startsWith("/api/interviews/schedules/my")) {
            return Permission.STUDENT_INTERVIEW_WRITE;
        }
        return Permission.AUTH_SELF;
    }

    private Permission aiPermission(String path) {
        if (path.startsWith("/api/ai/observability")) {
            return Permission.AI_OBSERVABILITY_READ;
        }
        if (path.startsWith("/api/ai/knowledge/documents")
                || path.startsWith("/api/ai/knowledge/stats")
                || path.startsWith("/api/ai/knowledge/files")
                || path.startsWith("/api/ai/knowledge/ingestions")
                || path.startsWith("/api/ai/knowledge/vector/status")
                || path.startsWith("/api/ai/knowledge/index")) {
            return Permission.AI_OBSERVABILITY_READ;
        }
        if (path.startsWith("/api/ai/knowledge/search") || path.startsWith("/api/ai/knowledge/answer")) {
            return Permission.AI_ANALYZE;
        }
        if (path.startsWith("/api/ai/candidates")) {
            return Permission.COMPANY_SCREENING_WRITE;
        }
        if (path.startsWith("/api/ai/interview")) {
            return Permission.STUDENT_INTERVIEW_WRITE;
        }
        if (path.startsWith("/api/ai/coach")
                || path.startsWith("/api/ai/learning")
                || path.startsWith("/api/ai/resume/rewrite")
                || path.startsWith("/api/ai/resume/draft")
                || path.startsWith("/api/ai/career")) {
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
