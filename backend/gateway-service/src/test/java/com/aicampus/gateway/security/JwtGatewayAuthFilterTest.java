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

        MockServerWebExchange studentScreeningTask = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/candidates/screen/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());

        filter.filter(studentScreeningTask, passThrough()).block();

        assertThat(studentScreeningTask.getResponse().getStatusCode().value()).isEqualTo(403);

        MockServerWebExchange studentOwnScreenings = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ai/screenings/my")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());
        CapturingChain studentOwnScreeningsChain = new CapturingChain();

        filter.filter(studentOwnScreenings, studentOwnScreeningsChain).block();

        assertThat(studentOwnScreenings.getResponse().getStatusCode()).isNull();
        assertThat(studentOwnScreeningsChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");

        MockServerWebExchange studentCoach = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/coach/advice")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());
        CapturingChain studentCoachChain = new CapturingChain();

        filter.filter(studentCoach, studentCoachChain).block();

        assertThat(studentCoach.getResponse().getStatusCode()).isNull();
        assertThat(studentCoachChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");

        String companyToken = jwtTokenService.issue("C001", "Company", Role.COMPANY);
        MockServerWebExchange companyInterview = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/interview/questions")
                        .header("Authorization", "Bearer " + companyToken)
                        .build());

        filter.filter(companyInterview, passThrough()).block();

        assertThat(companyInterview.getResponse().getStatusCode().value()).isEqualTo(403);

        MockServerWebExchange companyCoach = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/coach/advice")
                        .header("Authorization", "Bearer " + companyToken)
                        .build());

        filter.filter(companyCoach, passThrough()).block();

        assertThat(companyCoach.getResponse().getStatusCode().value()).isEqualTo(403);

        MockServerWebExchange companyScreeningTask = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/candidates/screen/tasks")
                        .header("Authorization", "Bearer " + companyToken)
                        .build());
        CapturingChain companyScreeningTaskChain = new CapturingChain();

        filter.filter(companyScreeningTask, companyScreeningTaskChain).block();

        assertThat(companyScreeningTask.getResponse().getStatusCode()).isNull();
        assertThat(companyScreeningTaskChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("COMPANY");
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

    @Test
    void protectsNotificationAndInterviewScheduleApisByRole() {
        String studentToken = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange studentNotifications = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notifications/my")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());
        CapturingChain studentNotificationChain = new CapturingChain();

        filter.filter(studentNotifications, studentNotificationChain).block();

        assertThat(studentNotifications.getResponse().getStatusCode()).isNull();
        assertThat(studentNotificationChain.exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("S001");

        MockServerWebExchange studentCreateSchedule = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/interviews/schedules")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());

        filter.filter(studentCreateSchedule, passThrough()).block();

        assertThat(studentCreateSchedule.getResponse().getStatusCode().value()).isEqualTo(403);

        String companyToken = jwtTokenService.issue("C001", "Company", Role.COMPANY);
        MockServerWebExchange companyCreateSchedule = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/interviews/schedules")
                        .header("Authorization", "Bearer " + companyToken)
                        .build());
        CapturingChain companyCreateScheduleChain = new CapturingChain();

        filter.filter(companyCreateSchedule, companyCreateScheduleChain).block();

        assertThat(companyCreateSchedule.getResponse().getStatusCode()).isNull();
        assertThat(companyCreateScheduleChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("COMPANY");

        MockServerWebExchange companyNotifications = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notifications/company")
                        .header("Authorization", "Bearer " + companyToken)
                        .build());
        CapturingChain companyNotificationChain = new CapturingChain();

        filter.filter(companyNotifications, companyNotificationChain).block();

        assertThat(companyNotifications.getResponse().getStatusCode()).isNull();
        assertThat(companyNotificationChain.exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("C001");
    }

    @Test
    void protectsKnowledgeWriteButAllowsRoleKnowledgeSearch() {
        String studentToken = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange studentCreateKnowledge = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/knowledge/documents")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());

        filter.filter(studentCreateKnowledge, passThrough()).block();

        assertThat(studentCreateKnowledge.getResponse().getStatusCode().value()).isEqualTo(403);

        MockServerWebExchange studentKnowledgeSearch = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/knowledge/search")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());
        CapturingChain studentKnowledgeSearchChain = new CapturingChain();

        filter.filter(studentKnowledgeSearch, studentKnowledgeSearchChain).block();

        assertThat(studentKnowledgeSearch.getResponse().getStatusCode()).isNull();
        assertThat(studentKnowledgeSearchChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");

        MockServerWebExchange studentKnowledgeAnswer = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/knowledge/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());
        CapturingChain studentKnowledgeAnswerChain = new CapturingChain();

        filter.filter(studentKnowledgeAnswer, studentKnowledgeAnswerChain).block();

        assertThat(studentKnowledgeAnswer.getResponse().getStatusCode()).isNull();
        assertThat(studentKnowledgeAnswerChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");

        String adminToken = jwtTokenService.issue("A001", "Admin", Role.ADMIN);
        MockServerWebExchange adminCreateKnowledge = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/knowledge/documents")
                        .header("Authorization", "Bearer " + adminToken)
                        .build());
        CapturingChain adminCreateKnowledgeChain = new CapturingChain();

        filter.filter(adminCreateKnowledge, adminCreateKnowledgeChain).block();

        assertThat(adminCreateKnowledge.getResponse().getStatusCode()).isNull();
        assertThat(adminCreateKnowledgeChain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void limitsAdminAuditToAdminRole() {
        String studentToken = jwtTokenService.issue("S001", "Student", Role.STUDENT);
        MockServerWebExchange studentAudit = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/audit/overview")
                        .header("Authorization", "Bearer " + studentToken)
                        .build());

        filter.filter(studentAudit, passThrough()).block();

        assertThat(studentAudit.getResponse().getStatusCode().value()).isEqualTo(403);

        String adminToken = jwtTokenService.issue("A001", "Admin", Role.ADMIN);
        MockServerWebExchange adminAuditExport = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/admin/audit/export")
                        .header("Authorization", "Bearer " + adminToken)
                        .build());
        CapturingChain chain = new CapturingChain();

        filter.filter(adminAuditExport, chain).block();

        assertThat(adminAuditExport.getResponse().getStatusCode()).isNull();
        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst("X-User-Permissions"))
                .contains("admin:audit:read")
                .contains("admin:audit:export");
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
