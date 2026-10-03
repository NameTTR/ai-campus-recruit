package com.aicampus.gateway.security;

import com.aicampus.common.enums.Role;
import com.aicampus.common.security.JwtTokenService;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=gateway-integration-test-secret",
        "security.jwt.issuer=gateway-integration-test",
        "security.gateway.public-docs-enabled=false",
        "security.gateway.auth.enabled=true",
        "spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-origin-patterns=http://localhost",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.sentinel.enabled=false",
        "management.endpoints.web.exposure.include=health,info",
        "logging.level.root=WARN"
})
class GatewaySecurityIntegrationTest {
    private final JwtTokenService tokens = new JwtTokenService(
            "gateway-integration-test-secret", "gateway-integration-test", 300);

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void createClient() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void privateGatewayDocumentationRequiresAuthentication() {
        client.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.code").isEqualTo(401);
    }

    @Test
    void managementEndpointsRequireAnAdministrator() {
        client.get().uri("/actuator/info").exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/actuator/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue("S001", "Student", Role.STUDENT))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/actuator/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue("A001", "Admin", Role.ADMIN))
                .exchange().expectStatus().isOk()
                .expectHeader().exists("X-Request-Id");
    }

    @Test
    void healthRemainsPublicAndReceivesTheNormalizedRequestId() {
        client.get().uri("/actuator/health").header("X-Request-Id", "health-check-001")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "health-check-001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/%61dmin/dashboard", "/api;ignored=value/admin/dashboard"})
    void encodedAndMatrixPathsAreAuthorizedUsingTheResolvedRoute(String path) {
        client.get().uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue("S001", "Student", Role.STUDENT))
                .exchange().expectStatus().isForbidden()
                .expectHeader().exists("X-Request-Id");
    }

    @Test
    void invalidRequestIdsAreReplacedWithASafeIdentifier() {
        client.get().uri("/actuator/health").header("X-Request-Id", "untrusted/id")
                .exchange().expectStatus().isOk()
                .expectHeader().valueMatches("X-Request-Id", "[A-Za-z0-9._-]{1,64}");
    }
    @Test
    void unauthorizedBrowserRequestsRetainCorsHeadersAndRequestIds() {
        client.get().uri("/api/jobs").header(HttpHeaders.ORIGIN, "http://localhost")
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-Id")
                .expectHeader().exists("X-Request-Id");
    }

    @Test
    void logoutCorsPreflightDoesNotRequireOrRevokeAToken() {
        String token = tokens.issue("S001", "Student", Role.STUDENT);
        client.options().uri("/api/auth/logout")
                .header(HttpHeaders.ORIGIN, "http://localhost")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost")
                .expectHeader().exists("X-Request-Id");
        client.get().uri("/v3/api-docs/swagger-config")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();
    }
}