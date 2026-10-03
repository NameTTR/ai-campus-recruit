package com.aicampus.delivery.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JobOwnershipClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://jobs.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final JobOwnershipClient client = new JobOwnershipClient(builder.build());

    @Test
    void dynamicJobUsesItsActualCompanyInsteadOfDefaultCompany() {
        server.expect(requestTo("http://jobs.test/api/jobs/J-DYNAMIC"))
                .andExpect(header("X-User-Role", "ADMIN"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"jobId\":\"J-DYNAMIC\",\"companyId\":\"C-REAL\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.companyIdFor("J-DYNAMIC")).isEqualTo("C-REAL");
        server.verify();
    }

    @Test
    void unavailableJobServiceFailsWithoutAssigningDefaultCompany() {
        server.expect(requestTo("http://jobs.test/api/jobs/J-DYNAMIC")).andRespond(withServerError());
        assertThatThrownBy(() -> client.companyIdFor("J-DYNAMIC"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("was not saved");
    }

    @Test
    void missingJobDoesNotProduceUnrelatedCompanyDelivery() {
        server.expect(requestTo("http://jobs.test/api/jobs/J-MISSING"))
                .andRespond(withSuccess("{\"code\":1,\"message\":\"Job not found\",\"data\":null}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.companyIdFor("J-MISSING"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Job not found");
    }

    @Test
    void invalidCompanyIsRejected() {
        server.expect(requestTo("http://jobs.test/api/jobs/J-BAD"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"jobId\":\"J-BAD\",\"companyId\":\"  \"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.companyIdFor("J-BAD"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ownership unavailable");
    }
}