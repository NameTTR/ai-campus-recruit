package com.aicampus.delivery.client;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class JobOwnershipClient {
    private final RestClient restClient;

    @Autowired
    public JobOwnershipClient(@Value("${services.job:${JOB_SERVICE_URI:http://localhost:8104}}") String jobServiceUri) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(jobServiceUri).requestFactory(factory).build();
    }

    JobOwnershipClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String companyIdFor(String jobId) {
        ApiResponse<JobSummary> response;
        try {
            response = restClient.get().uri("/api/jobs/{jobId}", jobId)
                    .header("X-User-Id", "internal-delivery")
                    .header("X-User-Role", "ADMIN")
                    .retrieve().body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException ex) {
            throw new IllegalStateException("Job service unavailable; the delivery was not saved", ex);
        }
        JobSummary job = response == null ? null : response.data();
        if (response == null || response.code() != 0 || job == null || !jobId.equals(job.jobId())) {
            throw new IllegalArgumentException("Job not found; the delivery was not saved");
        }
        if (job.companyId() == null || job.companyId().isBlank()) {
            throw new IllegalStateException("Job ownership unavailable; the delivery was not saved");
        }
        return job.companyId().trim();
    }
}