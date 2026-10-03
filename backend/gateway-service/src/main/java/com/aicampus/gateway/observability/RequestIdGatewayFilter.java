package com.aicampus.gateway.observability;

import java.util.regex.Pattern;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Creates one bounded request identifier at the edge and forwards it to every
 * downstream service. The response exposes the same value so support logs can
 * be correlated with a browser or API client request.
 */
@Component
public class RequestIdGatewayFilter implements WebFilter, Ordered {
    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = normalized(exchange.getRequest().getHeaders().getFirst(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        String finalRequestId = requestId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER, finalRequestId)
                .build();
        ServerWebExchange updated = exchange.mutate().request(request).build();
        updated.getResponse().beforeCommit(() -> {
            updated.getResponse().getHeaders().set(HEADER, finalRequestId);
            return Mono.empty();
        });
        return chain.filter(updated);
    }

    @Override
    public int getOrder() {
        return -400;
    }

    private static String normalized(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        return SAFE_ID.matcher(candidate).matches() ? candidate : null;
    }
}
