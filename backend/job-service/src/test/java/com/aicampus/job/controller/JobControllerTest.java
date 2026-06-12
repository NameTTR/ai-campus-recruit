package com.aicampus.job.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import com.aicampus.job.JobServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = JobServiceApplication.class, properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
class JobControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsSeedJob() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.data[0].jobId").value("J001"));
    }

    @Test
    void createJobReturnsGeneratedId() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "C001",
                                  "title": "测试开发实习生",
                                  "city": "上海",
                                  "salaryRange": "180-240/天",
                                  "requiredSkills": ["Java", "JUnit"],
                                  "description": "参与质量平台建设"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("测试开发实习生"))
                .andExpect(jsonPath("$.data.requiredSkills[1]").value("JUnit"));
    }

    @Test
    void createJobUsesCompanyHeaderBeforeRequestCompanyId() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .header("X-User-Id", "C-GATEWAY-001")
                        .header("X-User-Role", "COMPANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "C-BODY-001",
                                  "title": "Gateway Identity Developer",
                                  "city": "Shanghai",
                                  "salaryRange": "200-260/day",
                                  "requiredSkills": ["Java", "Spring Cloud"],
                                  "description": "Build trusted identity APIs"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value("C-GATEWAY-001"))
                .andExpect(jsonPath("$.data.title").value("Gateway Identity Developer"));
    }

    @Test
    void analyzeFallsBackWhenAiServiceIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/jobs/J001/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSummary").isNotEmpty());
    }
}
