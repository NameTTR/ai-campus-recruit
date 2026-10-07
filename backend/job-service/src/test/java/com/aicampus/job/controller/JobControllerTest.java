package com.aicampus.job.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.job.JobServiceApplication;
import com.aicampus.common.dto.JobPostRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Autowired
    private ObjectMapper objectMapper;

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
                .andExpect(jsonPath("$.data.companyName").value("C-JOB-OWNER"))
                .andExpect(jsonPath("$.data.requiredSkills[1]").value("Redis"));
    }

    @Test
    void companyDisplayNameIsTrimmedAndStoredWithoutChangingAuthenticatedOwner() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/jobs")
                        .headers(companyHeaders("C-DISPLAY-OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("C-FORGED", "  星河科技  ", "Display Name Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyId").value("C-DISPLAY-OWNER"))
                .andExpect(jsonPath("$.data.companyName").value("星河科技"))
                .andReturn();
        String jobId = jobId(created);

        mockMvc.perform(get("/api/jobs").headers(companyHeaders("C-DISPLAY-OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data[0].companyName").value("星河科技"));
        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .headers(companyHeaders("星河科技"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("C-DISPLAY-OWNER", "星河科技", "Forged By Display Name", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You do not have permission to update this job"));

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .headers(companyHeaders("C-DISPLAY-OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("C-DISPLAY-OWNER", "  星河科技研发中心  ", "Renamed Company Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyId").value("C-DISPLAY-OWNER"))
                .andExpect(jsonPath("$.data.companyName").value("星河科技研发中心"));
        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value("C-DISPLAY-OWNER"))
                .andExpect(jsonPath("$.data.companyName").value("星河科技研发中心"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void emptyCompanyDisplayNameFallsBackToOwnerOnCreate(String companyName) throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .headers(companyHeaders("C-BLANK-NAME"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("C-FORGED", companyName, "Blank Display Name Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyId").value("C-BLANK-NAME"))
                .andExpect(jsonPath("$.data.companyName").value("C-BLANK-NAME"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void emptyCompanyDisplayNamePreservesExistingNameOnUpdate(String companyName) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/jobs")
                        .headers(companyHeaders("C-PRESERVE-NAME"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("C-PRESERVE-NAME", "星河科技", "Keep Display Name Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String jobId = jobId(created);

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .headers(companyHeaders("C-PRESERVE-NAME"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("C-PRESERVE-NAME", companyName, "Updated Keep Display Name Job", "Redis")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyName").value("星河科技"));
        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .headers(companyHeaders("C-PRESERVE-NAME"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobBody("C-PRESERVE-NAME", "Legacy Update Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyName").value("星河科技"));
        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("星河科技"));
    }

    @Test
    void displayNameDoesNotAllowAnOwnerToTransferJobToAnotherCompany() throws Exception {
        String jobId = jobId(create("C-NO-TRANSFER", "Original Owner Job", "Java"));

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .headers(companyHeaders("C-NO-TRANSFER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("C-OTHER-OWNER", "星河科技", "Transferred Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A company cannot transfer a job to another owner"));
        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value("C-NO-TRANSFER"))
                .andExpect(jsonPath("$.data.companyName").value("C-NO-TRANSFER"))
                .andExpect(jsonPath("$.data.title").value("Original Owner Job"));
    }

    @Test
    void administratorCanCreateAndTransferJobUsingCompanyIdWithSeparateDisplayName() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/jobs")
                        .header("X-User-Id", "A-DISPLAY-ADMIN")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namedJobBody("  C-ADMIN-CREATED  ", "  星河科技  ", "Admin Created Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyId").value("C-ADMIN-CREATED"))
                .andExpect(jsonPath("$.data.companyName").value("星河科技"))
                .andReturn();
        String jobId = jobId(created);

        mockMvc.perform(put("/api/jobs/{id}", jobId)
                        .header("X-User-Id", "A-DISPLAY-ADMIN")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobBody("C-ADMIN-TRANSFERRED", "Admin Transferred Job", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyId").value("C-ADMIN-TRANSFERRED"))
                .andExpect(jsonPath("$.data.companyName").value("星河科技"));
        mockMvc.perform(get("/api/jobs").headers(companyHeaders("C-ADMIN-CREATED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/jobs").headers(companyHeaders("C-ADMIN-TRANSFERRED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].jobId").value(jobId));
    }

    @Test
    void legacySixArgumentRequestConstructorRemainsCompatible() throws Exception {
        JobPostRequest request = new JobPostRequest("C-LEGACY-REQUEST", "Legacy Request Job", "Shanghai",
                "200-260/day", List.of("Java"), "Build production APIs.");

        mockMvc.perform(post("/api/jobs")
                        .headers(companyHeaders("C-LEGACY-REQUEST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyName").value("C-LEGACY-REQUEST"));
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

    private static String namedJobBody(String companyId, String companyName, String title, String... skills) {
        return JsonPath.parse(jobBody(companyId, title, skills))
                .put("$", "companyName", companyName)
                .jsonString();
    }

    private static org.springframework.http.HttpHeaders companyHeaders(String companyId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", companyId);
        headers.add("X-User-Role", "COMPANY");
        return headers;
    }
}
