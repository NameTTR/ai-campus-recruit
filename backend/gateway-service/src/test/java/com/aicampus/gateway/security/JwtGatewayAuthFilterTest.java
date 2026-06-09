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

        String companyToken = jwtTokenService.issue("C001", "Company", Role.COMPANY);
        MockServerWebExchange companyInterview = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/interview/questions")
                        .header("Authorization", "Bearer " + companyToken)
                        .build());

        filter.filter(companyInterview, passThrough()).block();

        assertThat(companyInterview.getResponse().getStatusCode().value()).isEqualTo(403);
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
