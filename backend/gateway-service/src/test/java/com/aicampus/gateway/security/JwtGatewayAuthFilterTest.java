package com.aicampus.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicampus.common.enums.Role;
import com.aicampus.common.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtGatewayAuthFilterTest {
    private static final String SECRET = "gateway-test-secret-that-is-long-enough";
    private final JwtGatewayAuthFilter filter = new JwtGatewayAuthFilter(SECRET, "ai-campus-test", 86400, true);
    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, "ai-campus-test", 86400);

    @Test
    void rejectsMissingTokenForProtectedApi() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/dashboard").build());

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void skipsAuthForNonApiPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/assets/index.js").build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.exchange.getRequest().getPath().value()).isEqualTo("/assets/index.js");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void skipsAuthForRegistration() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register").build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.exchange.getRequest().getPath().value()).isEqualTo("/api/auth/register");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsStudentTokenForAdminApi() {
        String token = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .build());

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void allowsAdminTokenAndAddsIdentityHeaders() {
        String token = jwtTokenService.issue("A001", "Admin", Role.ADMIN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("A001");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Permissions")).contains("admin:account:write");
    }

    @Test
    void replacesClientSuppliedIdentityHeadersWithTokenClaims() {
        String token = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/students/profile")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "A001")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Permissions", "admin:account:write")
                        .build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.exchange.getRequest().getHeaders().get("X-User-Id")).containsExactly("S001");
        assertThat(chain.exchange.getRequest().getHeaders().get("X-User-Role")).containsExactly("STUDENT");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Permissions")).doesNotContain("admin:account:write");
    }

    @Test
    void rejectsStudentFromPublishingJobs() {
        String token = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .build());

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void rejectsStudentFromAccountManagement() {
        String token = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + token)
                        .build());

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void allowsCompanyToPublishJobs() {
        String token = jwtTokenService.issue("C001", "Company", Role.COMPANY);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("COMPANY");
    }

    @Test
    void separatesStudentInterviewAndCompanyScreeningAiApis() {
        String studentToken = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange studentScreening = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/candidates/screen")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());

        filter.filter(studentScreening, passThrough()).block();

        assertThat(studentScreening.getResponse().getStatusCode().value()).isEqualTo(403);

        MockServerWebExchange studentOwnScreenings = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ai/screenings/my")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());
        CapturingChain studentOwnScreeningsChain = new CapturingChain();

        filter.filter(studentOwnScreenings, studentOwnScreeningsChain).block();

        assertThat(studentOwnScreenings.getResponse().getStatusCode()).isNull();
        assertThat(studentOwnScreeningsChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");

        String companyToken = jwtTokenService.issue("C001", "Company", Role.COMPANY);
        MockServerWebExchange companyInterview = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/interview/questions")
                        .header("Authorization", "Bearer " + companyToken)
                        .build());

        filter.filter(companyInterview, passThrough()).block();

        assertThat(companyInterview.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void limitsAiObservabilityToAdminRole() {
        String studentToken = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange studentObservability = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ai/observability/summary")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());

        filter.filter(studentObservability, passThrough()).block();

        assertThat(studentObservability.getResponse().getStatusCode().value()).isEqualTo(403);

        String adminToken = jwtTokenService.issue("A001", "Admin", Role.ADMIN);
        MockServerWebExchange adminObservability = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ai/observability/summary")
                        .header("Authorization", "Bearer " + adminToken)
                        .build());
        CapturingChain chain = new CapturingChain();

        filter.filter(adminObservability, chain).block();

        assertThat(adminObservability.getResponse().getStatusCode()).isNull();
        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    private GatewayFilterChain passThrough() {
        return exchange -> Mono.empty();
    }

    private static class CapturingChain implements GatewayFilterChain {
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange = exchange;
            return Mono.empty();
        }
    }
}
