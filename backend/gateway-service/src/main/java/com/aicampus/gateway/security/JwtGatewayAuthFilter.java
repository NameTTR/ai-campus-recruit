package com.aicampus.gateway.security;

import com.aicampus.common.enums.Role;
import com.aicampus.common.security.JwtTokenException;
import com.aicampus.common.security.JwtTokenService;
import com.aicampus.common.security.JwtTokenService.TokenClaims;
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
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.userId())
                    .header("X-User-Role", claims.role().name())
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
        if (path.startsWith("/api/admin")) {
            return role == Role.ADMIN;
        }
        if (path.startsWith("/api/auth/me")) {
            return true;
        }
        if (path.startsWith("/api/students") || path.startsWith("/api/resumes") || path.startsWith("/api/matches")) {
            return role == Role.STUDENT || role == Role.ADMIN;
        }
        if (path.startsWith("/api/deliveries")) {
            return isDeliveryAllowed(path, method, role);
        }
        if (path.startsWith("/api/jobs")) {
            return isJobAllowed(path, method, role);
        }
        if (path.startsWith("/api/ai")) {
            return isAiAllowed(path, method, role);
        }
        return path.startsWith("/api/");
    }

    private boolean isDeliveryAllowed(String path, HttpMethod method, Role role) {
        if (role == Role.ADMIN) {
            return true;
        }
        if (path.startsWith("/api/deliveries/company") || method == HttpMethod.PUT) {
            return role == Role.COMPANY;
        }
        if (path.startsWith("/api/deliveries/my") || method == HttpMethod.POST) {
            return role == Role.STUDENT;
        }
        return role == Role.STUDENT || role == Role.COMPANY;
    }

    private boolean isJobAllowed(String path, HttpMethod method, Role role) {
        if (role == Role.ADMIN) {
            return true;
        }
        if (method == HttpMethod.GET) {
            return role == Role.STUDENT || role == Role.COMPANY;
        }
        return role == Role.COMPANY;
    }

    private boolean isAiAllowed(String path, HttpMethod method, Role role) {
        if (role == Role.ADMIN) {
            return true;
        }
        if (path.startsWith("/api/ai/candidates")) {
            return role == Role.COMPANY;
        }
        if (path.startsWith("/api/ai/interview")) {
            return role == Role.STUDENT;
        }
        return role == Role.STUDENT || role == Role.COMPANY;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status.value() + ",\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
