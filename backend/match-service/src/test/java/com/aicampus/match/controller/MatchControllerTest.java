package com.aicampus.match.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.match.MatchServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MatchServiceApplication.class, properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
class MatchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void matchReturnsScoreAndSuggestions() throws Exception {
        mockMvc.perform(post("/api/matches/resume-job")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R001\",\"jobId\":\"J001\",\"studentId\":\"S001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(88))
                .andExpect(jsonPath("$.data.suggestions[0]").isNotEmpty());
    }

    @Test
    void matchUsesStudentHeaderBeforeRequestStudentId() throws Exception {
        mockMvc.perform(post("/api/matches/resume-job")
                        .header("X-User-Id", "S-GATEWAY-001")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-GATEWAY-001\",\"jobId\":\"J001\",\"studentId\":\"S-BODY-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("S-GATEWAY-001"))
                .andExpect(jsonPath("$.data.resumeId").value("R-GATEWAY-001"));
    }

    @Test
    void listByStudentReturnsSeedMatch() throws Exception {
        mockMvc.perform(get("/api/matches/student/S001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].matchId", hasItem("M001")));
    }

    @Test
    void listByStudentUsesStudentHeaderBeforePathVariable() throws Exception {
        mockMvc.perform(post("/api/matches/resume-job")
                        .header("X-User-Id", "S-GATEWAY-002")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"R-GATEWAY-002\",\"jobId\":\"J001\",\"studentId\":\"S-BODY-002\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/matches/student/S-BODY-002")
                        .header("X-User-Id", "S-GATEWAY-002")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentId").value("S-GATEWAY-002"));
    }
}
