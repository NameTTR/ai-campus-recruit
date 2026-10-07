package com.aicampus.ai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.aicampus.ai.AiServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AiServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "dashscope.api-key=",
        "ai.knowledge.seed.enabled=false"
})
@AutoConfigureMockMvc
class InterviewPracticeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void sourcesRejectUnsafeContextIdsWithAnApiError() throws Exception {
        mockMvc.perform(get("/api/ai/interview/sessions/sources")
                        .header("X-User-Id", "S-INTERVIEW-CONTROLLER")
                        .header("X-User-Role", "STUDENT")
                        .param("resumeId", "../private"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }

    @Test
    void sourcesRequireBothStudentHeaders() throws Exception {
        mockMvc.perform(get("/api/ai/interview/sessions/sources")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }

    @Test
    void sourcesRejectNonStudentRoles() throws Exception {
        mockMvc.perform(get("/api/ai/interview/sessions/sources")
                        .header("X-User-Id", "C-INTERVIEW-CONTROLLER")
                        .header("X-User-Role", "COMPANY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }

    @Test
    void legacyEvaluateRejectsAnUnknownSessionWithAnApiError() throws Exception {
        mockMvc.perform(post("/api/ai/interview/sessions/IS-unknown/questions/Q-unknown/evaluate")
                        .header("X-User-Id", "S-INTERVIEW-CONTROLLER")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }

    @Test
    void nullAnswerBodyReturnsBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/api/ai/interview/sessions/IS-unknown/questions/Q-unknown/attempts")
                        .header("X-User-Id", "S-INTERVIEW-CONTROLLER")
                        .header("X-User-Role", "STUDENT")
                        .contentType(APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }

    @Test
    void nullActionBodiesReturnBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/api/ai/interview/sessions/IS-unknown/next-actions/preview")
                        .header("X-User-Id", "S-INTERVIEW-CONTROLLER")
                        .header("X-User-Role", "STUDENT")
                        .contentType(APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));

        mockMvc.perform(post("/api/ai/interview/sessions/IS-unknown/next-actions/confirm")
                        .header("X-User-Id", "S-INTERVIEW-CONTROLLER")
                        .header("X-User-Role", "STUDENT")
                        .contentType(APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }

    @Test
    void nullSearchBodyReturnsBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }
}
