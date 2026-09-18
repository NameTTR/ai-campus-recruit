package com.aicampus.job.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.job.JobServiceApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = JobServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "demo.seed.enabled=false"
})
@AutoConfigureMockMvc
class JobControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void companyCreatesAndUpdatesItsOwnOpenJobWithoutFakeOwnerData() throws Exception {
        MvcResult created = create("C-JOB-OWNER", "Skills API Engineer", "Java", "Spring Boot");
        String jobId = jobId(created);

        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value("C-JOB-OWNER"))
                .andExpect(jsonPath("$.data.companyName").value("C-JOB-OWNER"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .headers(companyHeaders("C-JOB-OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobBody("C-JOB-OWNER", "Updated Skills API Engineer", "Java", "Redis")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Updated Skills API Engineer"))
                .andExpect(jsonPath("$.data.requiredSkills[1]").value("Redis"));
    }

    @Test
    void ownerCanCloseAJobAndClosedJobIsHiddenFromPublicButNotOwner() throws Exception {
        String jobId = jobId(create("C-JOB-CLOSE", "Closable Job", "Java"));

        mockMvc.perform(post("/api/jobs/{id}/status", jobId)
                        .headers(companyHeaders("C-JOB-CLOSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/api/jobs/{id}", jobId).headers(companyHeaders("C-JOB-CLOSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void otherCompanyCannotEditOrAnalyzeAJobAndUnknownIdsDoNotResolveToSeeds() throws Exception {
        String jobId = jobId(create("C-JOB-ONE", "Private Owner Job", "Java"));

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .headers(companyHeaders("C-JOB-TWO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobBody("C-JOB-TWO", "Forged", "Python")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(post("/api/jobs/{id}/analyze", jobId).headers(companyHeaders("C-JOB-TWO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/api/jobs/J-NOT-FOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job not found"));
    }

    @Test
    void createRequiresCompanyIdentityAndValidRequirements() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobBody("C-FORGED", "No Identity", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(post("/api/jobs")
                        .headers(companyHeaders("C-VALIDATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Invalid\",\"city\":\"Shanghai\",\"salaryRange\":\"200\",\"requiredSkills\":[],\"description\":\"Description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("At least one required skill is required"));
    }

    private MvcResult create(String companyId, String title, String... skills) throws Exception {
        return mockMvc.perform(post("/api/jobs")
                        .headers(companyHeaders(companyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobBody(companyId, title, skills)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    private static String jobId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.jobId");
    }

    private static String jobBody(String companyId, String title, String... skills) {
        String jsonSkills = java.util.Arrays.stream(skills)
                .map(skill -> "\"" + skill + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"companyId\":\"" + companyId + "\",\"title\":\"" + title
                + "\",\"city\":\"Shanghai\",\"salaryRange\":\"200-260/day\",\"requiredSkills\":["
                + jsonSkills + "],\"description\":\"Build production APIs with measured quality.\"}";
    }

    private static org.springframework.http.HttpHeaders companyHeaders(String companyId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", companyId);
        headers.add("X-User-Role", "COMPANY");
        return headers;
    }
}
