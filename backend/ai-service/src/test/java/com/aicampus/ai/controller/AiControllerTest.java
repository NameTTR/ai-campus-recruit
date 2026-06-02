package com.aicampus.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.ai.AiServiceApplication;
import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenResult;
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
    void statusShowsDashScopeFallbackWhenKeyIsMissing() throws Exception {
        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.provider").value("dashscope"))
                .andExpect(jsonPath("$.data.capabilities[0]").isNotEmpty())
                .andExpect(jsonPath("$.data.capabilities", hasItem("candidate-screening")))
                .andExpect(jsonPath("$.data.fallbackReason").value("DASHSCOPE_API_KEY is not configured"));
    }

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
    void candidateScreeningParsesDashScopeJsonWhenConfigured() {
        AiCoachService service = new AiCoachService(new StubDashScopeClient("""
                {
                  "deliveryId": "D-AI-001",
                  "studentId": "S-AI-001",
                  "jobId": "J-AI-001",
                  "score": 91,
                  "recommendation": "建议进入一面",
                  "strengths": ["Java 基础扎实", "Spring Boot 项目经验匹配"],
                  "risks": ["项目量化指标不足", "Redis 高并发细节待确认"],
                  "interviewQuestions": ["请说明 MySQL 索引优化案例", "请说明 Redis 缓存失效处理"],
                  "nextActions": ["安排一面", "追问项目数据指标"],
                  "mocked": true
                }
                """));

        CandidateScreenResult result = service.screenCandidate(new CandidateScreenRequest(
                "D-REQUEST-001",
                "S-REQUEST-001",
                "R-REQUEST-001",
                "J-REQUEST-001",
                "Java 后端实习生",
                java.util.List.of("Java", "Spring Boot", "MySQL", "Redis"),
                java.util.List.of("校园招聘平台"),
                java.util.List.of("Java", "Spring Boot", "MySQL"),
                "有 Java 后端项目经验",
                "负责后端接口和数据库设计"));

        assertThat(result.deliveryId()).isEqualTo("D-AI-001");
        assertThat(result.studentId()).isEqualTo("S-AI-001");
        assertThat(result.jobId()).isEqualTo("J-AI-001");
        assertThat(result.score()).isEqualTo(91);
        assertThat(result.recommendation()).isEqualTo("建议进入一面");
        assertThat(result.strengths()).hasSize(2);
        assertThat(result.risks()).hasSize(2);
        assertThat(result.interviewQuestions()).hasSize(2);
        assertThat(result.nextActions()).hasSize(2);
        assertThat(result.mocked()).isFalse();
    }

    @Test
    void candidateScreeningUsesMockWhenDashScopeKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/ai/candidates/screen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryId": "D-SCREEN-001",
                                  "studentId": "S-SCREEN-001",
                                  "resumeId": "R-SCREEN-001",
                                  "jobId": "J-SCREEN-001",
                                  "targetRole": "Java 后端实习生",
                                  "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
                                  "projects": ["校园招聘平台", "简历诊断模块"],
                                  "jobRequirements": ["Java", "Spring Boot", "MySQL", "Redis"],
                                  "resumeSummary": "熟悉 Java 后端开发，有 Spring Boot 和 MySQL 项目经验。",
                                  "jobDescription": "负责后端接口开发、数据库设计和缓存优化。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deliveryId").value("D-SCREEN-001"))
                .andExpect(jsonPath("$.data.studentId").value("S-SCREEN-001"))
                .andExpect(jsonPath("$.data.jobId").value("J-SCREEN-001"))
                .andExpect(jsonPath("$.data.score").value(greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$.data.recommendation").value("建议进入一面"))
                .andExpect(jsonPath("$.data.strengths.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.risks.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.interviewQuestions.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.nextActions.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.mocked").value(true));
    }

    @Test
    void candidateScreeningRecordsInitiallyReturnsEmptyArrayForUnknownCompany() throws Exception {
        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .param("companyId", "C-HISTORY-EMPTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void candidateScreeningSavesRecordThatCanBeQueried() throws Exception {
        screenCandidate("C-HISTORY-001", "D-HISTORY-001", "S-HISTORY-001", "J-HISTORY-001");

        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .param("companyId", "C-HISTORY-001")
                        .param("deliveryId", "D-HISTORY-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].screeningId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].companyId").value("C-HISTORY-001"))
                .andExpect(jsonPath("$.data[0].deliveryId").value("D-HISTORY-001"))
                .andExpect(jsonPath("$.data[0].studentId").value("S-HISTORY-001"))
                .andExpect(jsonPath("$.data[0].jobId").value("J-HISTORY-001"))
                .andExpect(jsonPath("$.data[0].score").value(greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$.data[0].recommendation").isNotEmpty())
                .andExpect(jsonPath("$.data[0].strengths.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data[0].risks.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data[0].interviewQuestions.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data[0].nextActions.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data[0].mocked").value(true))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty());
    }

    @Test
    void candidateScreeningRecordsCanBeFilteredByCompanyIdAndDeliveryId() throws Exception {
        screenCandidate("C-HISTORY-FILTER-A", "D-HISTORY-FILTER-A-1", "S-HISTORY-FILTER-A-1", "J-HISTORY-FILTER-A-1");
        screenCandidate("C-HISTORY-FILTER-A", "D-HISTORY-FILTER-A-2", "S-HISTORY-FILTER-A-2", "J-HISTORY-FILTER-A-2");
        screenCandidate("C-HISTORY-FILTER-B", "D-HISTORY-FILTER-B-1", "S-HISTORY-FILTER-B-1", "J-HISTORY-FILTER-B-1");

        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .param("companyId", "C-HISTORY-FILTER-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].companyId").value("C-HISTORY-FILTER-A"))
                .andExpect(jsonPath("$.data[1].companyId").value("C-HISTORY-FILTER-A"));

        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .param("deliveryId", "D-HISTORY-FILTER-B-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].companyId").value("C-HISTORY-FILTER-B"))
                .andExpect(jsonPath("$.data[0].deliveryId").value("D-HISTORY-FILTER-B-1"));
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
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data[0].question").isNotEmpty())
                .andExpect(jsonPath("$.data[1].question").isNotEmpty())
                .andExpect(jsonPath("$.data[2].question").isNotEmpty())
                .andExpect(jsonPath("$.data[0].referencePoints[0]").isNotEmpty());
    }

    @Test
    void interviewRecordsInitiallyReturnsArray() throws Exception {
        mockMvc.perform(get("/api/ai/interview/records")
                        .param("studentId", "S-EMPTY-RECORDS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void interviewFeedbackSavesMockRecordWhenDashScopeKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/ai/interview/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S-RECORD-001",
                                  "questionId": "IQ-RECORD-001",
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

        mockMvc.perform(get("/api/ai/interview/records")
                        .param("studentId", "S-RECORD-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].studentId").value("S-RECORD-001"))
                .andExpect(jsonPath("$.data[0].questionId").value("IQ-RECORD-001"))
                .andExpect(jsonPath("$.data[0].score").isNumber())
                .andExpect(jsonPath("$.data[0].suggestions[0]").isNotEmpty())
                .andExpect(jsonPath("$.data[0].mocked").value(true));
    }

    private void screenCandidate(String companyId, String deliveryId, String studentId, String jobId) throws Exception {
        mockMvc.perform(post("/api/ai/candidates/screen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "deliveryId": "%s",
                                  "studentId": "%s",
                                  "resumeId": "R-HISTORY-001",
                                  "jobId": "%s",
                                  "targetRole": "Java Backend Intern",
                                  "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
                                  "projects": ["Campus recruitment platform"],
                                  "jobRequirements": ["Java", "Spring Boot", "MySQL", "Redis"],
                                  "resumeSummary": "Java backend project experience",
                                  "jobDescription": "Build backend APIs and database features"
                                }
                                """.formatted(companyId, deliveryId, studentId, jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId))
                .andExpect(jsonPath("$.data.studentId").value(studentId))
                .andExpect(jsonPath("$.data.jobId").value(jobId));
    }

    private static final class StubDashScopeClient extends DashScopeClient {
        private final String response;

        private StubDashScopeClient(String response) {
            super("test-key", "qwen-plus", "http://localhost");
            this.response = response;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, boolean jsonResponse) {
            assertThat(systemPrompt).isNotBlank();
            assertThat(userPrompt).contains("只返回 JSON 对象");
            assertThat(jsonResponse).isTrue();
            return response;
        }
    }
}
