package com.aicampus.ai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.ai.AiServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AiServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "dashscope.api-key="
})
@AutoConfigureMockMvc
class AiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyzeUsesMockWhenDashScopeKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"resume\",\"content\":\"Java Spring Boot\",\"context\":\"Java 后端\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mocked").value(true))
                .andExpect(jsonPath("$.data.provider").value("mock-dashscope"));
    }

    @Test
    void interviewQuestionGenerationReturnsMultipleQuestions() throws Exception {
        mockMvc.perform(post("/api/ai/interview/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S001",
                                  "resumeId": "R001",
                                  "jobId": "J001",
                                  "targetRole": "Java 后端实习生",
                                  "skills": ["Java", "Spring Boot", "MySQL"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].question").isNotEmpty())
                .andExpect(jsonPath("$.data[1].question").isNotEmpty())
                .andExpect(jsonPath("$.data[0].referencePoints[0]").isNotEmpty());
    }

    @Test
    void interviewFeedbackUsesMockWhenDashScopeKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/ai/interview/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S001",
                                  "questionId": "IQ-001",
                                  "question": "请说明你如何排查接口响应变慢。",
                                  "answer": "我会先查看日志和监控，再检查慢 SQL、索引和缓存命中率，最后补充压测复现。",
                                  "targetRole": "Java 后端实习生"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mocked").value(true))
                .andExpect(jsonPath("$.data.score").isNumber())
                .andExpect(jsonPath("$.data.suggestions[0]").isNotEmpty());
    }
}
