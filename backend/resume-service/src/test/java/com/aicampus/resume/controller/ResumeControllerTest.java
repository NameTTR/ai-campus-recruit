package com.aicampus.resume.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.resume.ResumeServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ResumeServiceApplication.class, properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
class ResumeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadReturnsResumeSummary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "demo".getBytes());
        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data.studentId").value("S001"))
                .andExpect(jsonPath("$.data.storageProvider").value("local-demo"))
                .andExpect(jsonPath("$.data.storageStatus").value("SKIPPED"));
    }

    @Test
    void analyzeFallsBackWhenAiServiceIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/resumes/R001/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(86))
                .andExpect(jsonPath("$.data.objectKey").value("resumes/R001/demo-resume.pdf"))
                .andExpect(jsonPath("$.data.skills[4]").value("Docker"));
    }
}
