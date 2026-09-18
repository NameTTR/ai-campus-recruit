package com.aicampus.match.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.match.MatchServiceApplication;
import com.aicampus.match.client.JobClient;
import com.aicampus.match.client.ResumeClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MatchServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "demo.seed.enabled=false"
})
@AutoConfigureMockMvc
class MatchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResumeClient resumeClient;

    @MockBean
    private JobClient jobClient;

    @BeforeEach
    void configureClients() {
        when(resumeClient.detail(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return "R-REAL".equals(id) ? ApiResponse.ok(resume()) : ApiResponse.fail("Resume not found");
        });
        when(jobClient.detail(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return "J-REAL".equals(id) ? ApiResponse.ok(job()) : ApiResponse.fail("Job not found");
        });
    }

    @Test
    void matchUsesFetchedResumeAndJobSkillSnapshotsInsteadOfAConstantScore() throws Exception {
        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(studentHeaders("S-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-REAL\",\"jobId\":\"J-REAL\",\"studentId\":\"S-REAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.score").value(50))
                .andExpect(jsonPath("$.data.matchedSkills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.missingSkills[0]").value("Docker"))
                .andExpect(jsonPath("$.data.analysisSource").value("RULE_SKILL_COVERAGE"))
                .andExpect(jsonPath("$.data.resumeSkillsSnapshot[0]").value("Java"))
                .andExpect(jsonPath("$.data.requiredSkillsSnapshot[1]").value("Docker"));
    }

    @Test
    void studentCannotForgeAnotherStudentOrMatchUnknownResources() throws Exception {
        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(studentHeaders("S-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-REAL\",\"jobId\":\"J-REAL\",\"studentId\":\"S-FORGED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(studentHeaders("S-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-NOT-FOUND\",\"jobId\":\"J-REAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Resume not found"));
    }

    @Test
    void closedJobsCannotBeMatchedByStudentsOrAdministrators() throws Exception {
        when(jobClient.detail(anyString(), anyString(), anyString()))
                .thenReturn(ApiResponse.ok(job(List.of("Spring Boot"), "CLOSED")));

        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(studentHeaders("S-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-REAL\",\"jobId\":\"J-REAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("The requested job is not open"));
        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(adminHeaders("A-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-REAL\",\"jobId\":\"J-REAL\",\"studentId\":\"S-REAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("The requested job is not open"));
    }

    @Test
    void missingJobSkillRequirementsProduceAnInsufficientEvidenceResult() throws Exception {
        when(jobClient.detail(anyString(), anyString(), anyString()))
                .thenReturn(ApiResponse.ok(job(List.of(), "OPEN")));

        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(studentHeaders("S-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-REAL\",\"jobId\":\"J-REAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.analysisSource").value("RULE_INSUFFICIENT_JOB_SKILLS"))
                .andExpect(jsonPath("$.data.requiredSkillsSnapshot.length()").value(0))
                .andExpect(jsonPath("$.data.gaps[0]").value("岗位未配置技能要求，无法进行可靠匹配。"))
                .andExpect(jsonPath("$.data.suggestions[0]").value("请补充岗位技能要求后重新匹配。"));
    }

    @Test
    void emptyResumeSkillsExposeEveryJobRequirementAsMissing() throws Exception {
        when(resumeClient.detail(anyString(), anyString(), anyString()))
                .thenReturn(ApiResponse.ok(resume(List.of())));

        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(studentHeaders("S-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-REAL\",\"jobId\":\"J-REAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.analysisSource").value("RULE_SKILL_COVERAGE"))
                .andExpect(jsonPath("$.data.missingSkills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.missingSkills[1]").value("Docker"));
    }

    @Test
    void studentAndCompanyHistoryViewsEnforceOwnership() throws Exception {
        mockMvc.perform(post("/api/matches/resume-job")
                        .headers(studentHeaders("S-REAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-REAL\",\"jobId\":\"J-REAL\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/matches/student/S-OTHER").headers(studentHeaders("S-REAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/api/matches/job/J-REAL").headers(companyHeaders("C-REAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/matches/job/J-REAL").headers(companyHeaders("C-OTHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    private static ResumeSummary resume() {
        return resume(List.of("Java", "SpringBoot"));
    }

    private static ResumeSummary resume(List<String> skills) {
        return new ResumeSummary("R-REAL", "S-REAL", "resume.docx", "Bachelor", skills,
                List.of("Project API"), "Extracted", 40, "key", "local", "SKIPPED", "DOCX", "TEXT_EXTRACTED", 120);
    }

    private static JobSummary job() {
        return job(List.of("Spring Boot", "Docker"), "OPEN");
    }

    private static JobSummary job(List<String> requiredSkills, String status) {
        return new JobSummary("J-REAL", "C-REAL", "C-REAL", "Platform Engineer", "Shanghai", "200/day",
                requiredSkills, "Build platform APIs", "Not analyzed", status);
    }

    private static org.springframework.http.HttpHeaders studentHeaders(String studentId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", studentId);
        headers.add("X-User-Role", "STUDENT");
        return headers;
    }

    private static org.springframework.http.HttpHeaders companyHeaders(String companyId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", companyId);
        headers.add("X-User-Role", "COMPANY");
        return headers;
    }

    private static org.springframework.http.HttpHeaders adminHeaders(String adminId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", adminId);
        headers.add("X-User-Role", "ADMIN");
        return headers;
    }
}
