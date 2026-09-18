package com.aicampus.gateway.docs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class OpenApiAggregationControllerTest {
    private final OpenApiAggregationController controller = new OpenApiAggregationController();

    @Test
    void exposesAllServiceOpenApiUrlsForGatewayKnife4j() {
        OpenApiAggregationController.SwaggerConfig config = controller.swaggerConfig(
                        MockServerHttpRequest.get("http://localhost:18080/v3/api-docs/swagger-config").build())
                .block();

        assertThat(config).isNotNull();
        assertThat(config.configUrl()).isEqualTo("/v3/api-docs/swagger-config");
        assertThat(config.url()).isEqualTo("/v3/api-docs");
        assertThat(config.urls())
                .extracting(OpenApiAggregationController.OpenApiUrl::url)
                .containsExactly(
                        "/v3/api-docs",
                        "/v3/api-docs/auth-service",
                        "/v3/api-docs/user-service",
                        "/v3/api-docs/resume-service",
                        "/v3/api-docs/job-service",
                        "/v3/api-docs/match-service",
                        "/v3/api-docs/ai-service",
                        "/v3/api-docs/delivery-service");
    }
}
