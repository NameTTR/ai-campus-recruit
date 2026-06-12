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
import com.jayway.jsonpath.JsonPath;
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
    void intelligentSearchReturnsRankedResultsAndIsObservable() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Java Redis backend",
                                  "role": "STUDENT",
                                  "limit": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.query").value("Java Redis backend"))
                .andExpect(jsonPath("$.data.results.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.results[0].score").value(greaterThanOrEqualTo(40)))
                .andExpect(jsonPath("$.data.results[0].highlights[0]").isNotEmpty());

        mockMvc.perform(get("/api/ai/observability/calls")
                        .param("provider", "local-semantic-search")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].operation").value("semantic-search"))
                .andExpect(jsonPath("$.data[0].success").value(true));
    }

    @Test
    void knowledgeSearchReturnsSeededRagDocuments() throws Exception {
        mockMvc.perform(post("/api/ai/knowledge/search")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Java Redis interview",
                                  "role": "ADMIN",
                                  "limit": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.query").value("Java Redis interview"))
                .andExpect(jsonPath("$.data.results.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.results[0].type").value("knowledge"))
                .andExpect(jsonPath("$.data.results[0].score").value(greaterThanOrEqualTo(40)));
    }

    @Test
    void knowledgeStatsReflectsRealSeedCorpus() throws Exception {
        mockMvc.perform(get("/api/ai/knowledge/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.documentCount").value(greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$.data.chunkCount").value(greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$.data.categoryCounts.rag").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.roleCounts.ADMIN").value(greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$.data.sourceCounts['internal-corpus:v3.10']").value(greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$.data.corpusVersion").value("v3.10-campus-rag-corpus"))
                .andExpect(jsonPath("$.data.seedEnabled").value(true));
    }

    @Test
    void knowledgeSearchFindsThreeVmCorpusEvidence() throws Exception {
        mockMvc.perform(post("/api/ai/knowledge/search")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "three VM Docker Nacos RocketMQ",
                                  "role": "ADMIN",
                                  "limit": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.results.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.results[*].title").value(hasItem("Three VM microservice deployment topology")));
    }

    @Test
    void knowledgeDocumentsCanBeCreatedAndSearched() throws Exception {
        mockMvc.perform(post("/api/ai/knowledge/documents")
                        .header("X-User-Id", "A-KB-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Distributed load testing checklist",
                                  "content": "Validate gateway latency, delivery throughput, AI fallback status, Redis cache behavior, RocketMQ event lag and three VM health.",
                                  "category": "performance",
                                  "source": "admin-note",
                                  "tags": ["load-test", "gateway", "rocketmq"],
                                  "roles": ["ADMIN", "COMPANY"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.documentId").isNotEmpty())
                .andExpect(jsonPath("$.data.createdBy").value("A-KB-001"));

        mockMvc.perform(post("/api/ai/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "RocketMQ event lag",
                                  "role": "COMPANY",
                                  "limit": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.results[0].title").value("Distributed load testing checklist"));
    }

    @Test
    void knowledgeSearchUsesGatewayRoleBeforeRequestRole() throws Exception {
        mockMvc.perform(post("/api/ai/knowledge/documents")
                        .header("X-User-Id", "A-KB-PRIVATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Company private screening note",
                                  "content": "company-private-keyword should be visible only to company and admin roles.",
                                  "category": "screening",
                                  "source": "admin-note",
                                  "tags": ["private"],
                                  "roles": ["COMPANY", "ADMIN"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/ai/knowledge/search")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "company-private-keyword",
                                  "role": "COMPANY",
                                  "limit": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.results.length()").value(0));

        mockMvc.perform(post("/api/ai/knowledge/search")
                        .header("X-User-Role", "COMPANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "company-private-keyword",
                                  "role": "STUDENT",
                                  "limit": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void knowledgeAnswerFallsBackWithCitationsWhenDashScopeKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/ai/knowledge/answer")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Java Redis interview",
                                  "role": "ADMIN",
                                  "limit": 4,
                                  "useAi": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.query").value("Java Redis interview"))
                .andExpect(jsonPath("$.data.mocked").value(true))
                .andExpect(jsonPath("$.data.provider").value("local-rag-fallback"))
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("[1]")))
                .andExpect(jsonPath("$.data.citations.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.citations[0].chunkId").isNotEmpty());
    }

    @Test
    void knowledgeAnswerCanDisableExternalAiForLoadSmoke() throws Exception {
        mockMvc.perform(post("/api/ai/knowledge/answer")
                        .header("X-User-Role", "COMPANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "screening playbook",
                                  "role": "STUDENT",
                                  "limit": 3,
                                  "useAi": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mocked").value(true))
                .andExpect(jsonPath("$.data.citations.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.citations[0].title").value("Company candidate screening playbook"));
    }

    @Test
    void knowledgeSearchUsesChunkedLongDocuments() throws Exception {
        mockMvc.perform(post("/api/ai/knowledge/documents")
                        .header("X-User-Id", "A-KB-CHUNK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Long RAG chunking guide",
                                  "content": "Chunk one explains general backend topics. %s target-chunk-keyword validates that retrieval can find text placed after the first window with enough context for a citation.",
                                  "category": "rag",
                                  "source": "admin-note",
                                  "tags": ["chunking", "embedding"],
                                  "roles": ["STUDENT", "ADMIN"]
                                }
                                """.formatted("padding ".repeat(90))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/ai/knowledge/search")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "target-chunk-keyword citation",
                                  "role": "STUDENT",
                                  "limit": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.results[0].title").value("Long RAG chunking guide"));
    }

    @Test
    void coachAdviceUsesStudentHeaderAndRecordsObservability() throws Exception {
        mockMvc.perform(post("/api/ai/coach/advice")
                        .header("X-User-Id", "S-COACH-HTTP-001")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S-BODY-IGNORED",
                                  "targetRole": "Java Backend Intern",
                                  "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
                                  "recentDeliveries": ["D001 submitted", "D002 interview"],
                                  "interviewWeaknesses": ["needs stronger Redis examples"],
                                  "careerGoal": "Win a backend internship offer",
                                  "weeks": 6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.studentId").value("S-COACH-HTTP-001"))
                .andExpect(jsonPath("$.data.targetRole").value("Java Backend Intern"))
                .andExpect(jsonPath("$.data.priorityActions.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.learningPath.length()").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.data.mocked").value(true));

        mockMvc.perform(get("/api/ai/observability/calls")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].operation", hasItem("coach-advice")));
    }

    @Test
    void observabilitySummaryTracksMockedAiFallbacks() throws Exception {
        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"job\",\"content\":\"Spring Boot Redis\",\"context\":\"backend\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mocked").value(true));

        mockMvc.perform(get("/api/ai/observability/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("dashscope"))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.totalCalls").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.mockedCalls").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.recentCalls[0].operation").isNotEmpty());
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
                "PDF",
                "TEXT_EXTRACTED",
                128,
                "Java 后端实习生",
                java.util.List.of("Java", "Spring Boot", "MySQL", "Redis"),
                java.util.List.of("校园招聘平台"),
                java.util.List.of("Java", "Spring Boot", "MySQL"),
                "有 Java 后端项目经验",
                "负责后端接口和数据库设计"));

        assertThat(result.deliveryId()).isEqualTo("D-AI-001");
        assertThat(result.studentId()).isEqualTo("S-AI-001");
        assertThat(result.jobId()).isEqualTo("J-AI-001");
        assertThat(result.resumeSourceFormat()).isEqualTo("PDF");
        assertThat(result.resumeParseStatus()).isEqualTo("TEXT_EXTRACTED");
        assertThat(result.resumeParsedTextLength()).isEqualTo(128);
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
                                  "resumeSourceFormat": "DOCX",
                                  "resumeParseStatus": "TEXT_EXTRACTED",
                                  "resumeParsedTextLength": 256,
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
                .andExpect(jsonPath("$.data.resumeSourceFormat").value("DOCX"))
                .andExpect(jsonPath("$.data.resumeParseStatus").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.resumeParsedTextLength").value(256))
                .andExpect(jsonPath("$.data.score").value(greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$.data.recommendation").value("建议进入一面"))
                .andExpect(jsonPath("$.data.strengths.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.risks.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.interviewQuestions.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.nextActions.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.mocked").value(true));
    }

    @Test
    void planningHistoryReturnsGeneratedStudentRecords() throws Exception {
        mockMvc.perform(post("/api/ai/career/plan")
                        .header("X-User-Id", "S-HISTORY-HTTP-001")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S-BODY-IGNORED",
                                  "targetRole": "Java Backend Intern",
                                  "skills": ["Java", "Spring Boot"],
                                  "interests": ["backend"],
                                  "resumeSummary": "Java backend project experience",
                                  "timeframeWeeks": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("S-HISTORY-HTTP-001"));

        mockMvc.perform(get("/api/ai/career/history")
                        .header("X-User-Id", "S-HISTORY-HTTP-001")
                        .header("X-User-Role", "STUDENT")
                        .param("studentId", "S-BODY-IGNORED")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].studentId").value("S-HISTORY-HTTP-001"))
                .andExpect(jsonPath("$.data[0].operation").value("career-plan"));
    }

    @Test
    void candidateScreeningUsesCompanyHeaderBeforeRequestCompanyId() throws Exception {
        mockMvc.perform(post("/api/ai/candidates/screen")
                        .header("X-User-Id", "C-GATEWAY-TRUST-001")
                        .header("X-User-Role", "COMPANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "C-BODY-TRUST-001",
                                  "deliveryId": "D-GATEWAY-TRUST-001",
                                  "studentId": "S-GATEWAY-TRUST-001",
                                  "resumeId": "R-GATEWAY-TRUST-001",
                                  "jobId": "J-GATEWAY-TRUST-001",
                                  "resumeSourceFormat": "PDF",
                                  "resumeParseStatus": "TEXT_EXTRACTED",
                                  "resumeParsedTextLength": 96,
                                  "targetRole": "Java Backend Intern",
                                  "skills": ["Java", "Spring Boot"],
                                  "projects": ["Campus recruitment platform"],
                                  "jobRequirements": ["Java", "Spring Boot"],
                                  "resumeSummary": "Java backend project experience",
                                  "jobDescription": "Build backend APIs"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deliveryId").value("D-GATEWAY-TRUST-001"))
                .andExpect(jsonPath("$.data.studentId").value("S-GATEWAY-TRUST-001"));

        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .header("X-User-Id", "C-GATEWAY-TRUST-001")
                        .header("X-User-Role", "COMPANY")
                        .param("companyId", "C-BODY-TRUST-001")
                        .param("deliveryId", "D-GATEWAY-TRUST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].companyId").value("C-GATEWAY-TRUST-001"))
                .andExpect(jsonPath("$.data[0].deliveryId").value("D-GATEWAY-TRUST-001"));

        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .param("companyId", "C-BODY-TRUST-001")
                        .param("deliveryId", "D-GATEWAY-TRUST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void asyncCandidateScreeningTaskCompletesAndCreatesHistoryRecord() throws Exception {
        mockMvc.perform(post("/api/ai/candidates/screen/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "C-ASYNC-001",
                                  "deliveryId": "D-ASYNC-001",
                                  "studentId": "S-ASYNC-001",
                                  "resumeId": "R-ASYNC-001",
                                  "jobId": "J-ASYNC-001",
                                  "resumeSourceFormat": "DOCX",
                                  "resumeParseStatus": "TEXT_EXTRACTED",
                                  "resumeParsedTextLength": 188,
                                  "targetRole": "Java Backend Intern",
                                  "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
                                  "projects": ["Campus recruitment platform"],
                                  "jobRequirements": ["Java", "Spring Boot", "MySQL", "Redis"],
                                  "resumeSummary": "Java backend project experience",
                                  "jobDescription": "Build backend APIs"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andExpect(jsonPath("$.data.companyId").value("C-ASYNC-001"))
                .andExpect(jsonPath("$.data.deliveryId").value("D-ASYNC-001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.source").value("RUNTIME"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        waitUntilTaskCompleted("C-ASYNC-001", "D-ASYNC-001");

        mockMvc.perform(get("/api/ai/candidates/screen/tasks")
                        .param("companyId", "C-ASYNC-001")
                        .param("deliveryId", "D-ASYNC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].source").value("RUNTIME"))
                .andExpect(jsonPath("$.data[0].result.deliveryId").value("D-ASYNC-001"))
                .andExpect(jsonPath("$.data[0].result.resumeSourceFormat").value("DOCX"))
                .andExpect(jsonPath("$.data[0].result.resumeParseStatus").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data[0].result.resumeParsedTextLength").value(188))
                .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .param("companyId", "C-ASYNC-001")
                        .param("deliveryId", "D-ASYNC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deliveryId").value("D-ASYNC-001"))
                .andExpect(jsonPath("$.data[0].companyId").value("C-ASYNC-001"));
    }

    @Test
    void asyncCandidateScreeningTaskUsesCompanyHeaderBeforeRequestCompanyId() throws Exception {
        mockMvc.perform(post("/api/ai/candidates/screen/tasks")
                        .header("X-User-Id", "C-ASYNC-GATEWAY-001")
                        .header("X-User-Role", "COMPANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "C-ASYNC-BODY-001",
                                  "deliveryId": "D-ASYNC-GATEWAY-001",
                                  "studentId": "S-ASYNC-GATEWAY-001",
                                  "resumeId": "R-ASYNC-GATEWAY-001",
                                  "jobId": "J-ASYNC-GATEWAY-001",
                                  "targetRole": "Java Backend Intern",
                                  "skills": ["Java", "Spring Boot"],
                                  "projects": ["Campus recruitment platform"],
                                  "jobRequirements": ["Java", "Spring Boot"],
                                  "resumeSummary": "Java backend project experience",
                                  "jobDescription": "Build backend APIs"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value("C-ASYNC-GATEWAY-001"))
                .andExpect(jsonPath("$.data.deliveryId").value("D-ASYNC-GATEWAY-001"));

        mockMvc.perform(get("/api/ai/candidates/screen/tasks")
                        .header("X-User-Id", "C-ASYNC-GATEWAY-001")
                        .header("X-User-Role", "COMPANY")
                        .param("companyId", "C-ASYNC-BODY-001")
                        .param("deliveryId", "D-ASYNC-GATEWAY-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].companyId").value("C-ASYNC-GATEWAY-001"));

        mockMvc.perform(get("/api/ai/candidates/screen/tasks")
                        .param("companyId", "C-ASYNC-BODY-001")
                        .param("deliveryId", "D-ASYNC-GATEWAY-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void asyncCandidateScreeningTaskDetailCanBeQueriedByTaskId() throws Exception {
        String taskId = submitAsyncTask("C-ASYNC-DETAIL-001", "D-ASYNC-DETAIL-001");

        mockMvc.perform(get("/api/ai/candidates/screen/tasks/{taskId}", taskId)
                        .param("companyId", "C-ASYNC-DETAIL-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.companyId").value("C-ASYNC-DETAIL-001"))
                .andExpect(jsonPath("$.data.deliveryId").value("D-ASYNC-DETAIL-001"))
                .andExpect(jsonPath("$.data.source").value("RUNTIME"));
    }

    @Test
    void asyncCandidateScreeningTaskDetailIsIsolatedByCompanyHeader() throws Exception {
        String taskId = submitAsyncTask("C-ASYNC-DETAIL-OWNER", "D-ASYNC-DETAIL-OWNER");

        mockMvc.perform(get("/api/ai/candidates/screen/tasks/{taskId}", taskId)
                        .header("X-User-Id", "C-ASYNC-DETAIL-OTHER")
                        .header("X-User-Role", "COMPANY")
                        .param("companyId", "C-ASYNC-DETAIL-OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/ai/candidates/screen/tasks/{taskId}", taskId)
                        .header("X-User-Id", "C-ASYNC-DETAIL-OWNER")
                        .header("X-User-Role", "COMPANY")
                        .param("companyId", "C-ASYNC-DETAIL-OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyId").value("C-ASYNC-DETAIL-OWNER"));
    }

    @Test
    void retryRejectedForNonFailedAsyncCandidateScreeningTask() throws Exception {
        String taskId = submitAsyncTask("C-ASYNC-RETRY-ACTIVE", "D-ASYNC-RETRY-ACTIVE");

        mockMvc.perform(post("/api/ai/candidates/screen/tasks/{taskId}/retry", taskId)
                        .param("companyId", "C-ASYNC-RETRY-ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").doesNotExist());
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
                .andExpect(jsonPath("$.data[0].resumeSourceFormat").value("PDF"))
                .andExpect(jsonPath("$.data[0].resumeParseStatus").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data[0].resumeParsedTextLength").value(96))
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
    void adminCanQueryCandidateScreeningRecordsByRequestCompanyId() throws Exception {
        screenCandidate("C-ADMIN-VIEW-001", "D-ADMIN-VIEW-001", "S-ADMIN-VIEW-001", "J-ADMIN-VIEW-001");

        mockMvc.perform(get("/api/ai/candidates/screenings")
                        .header("X-User-Id", "A-ADMIN-001")
                        .header("X-User-Role", "ADMIN")
                        .param("companyId", "C-ADMIN-VIEW-001")
                        .param("deliveryId", "D-ADMIN-VIEW-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].companyId").value("C-ADMIN-VIEW-001"));
    }

    @Test
    void studentCanQueryOwnCandidateScreeningFeedback() throws Exception {
        screenCandidate("C-STUDENT-LOOP-001", "D-STUDENT-LOOP-001", "S-STUDENT-LOOP-001", "J-STUDENT-LOOP-001");
        screenCandidate("C-STUDENT-LOOP-001", "D-STUDENT-LOOP-002", "S-OTHER-LOOP-001", "J-STUDENT-LOOP-001");

        mockMvc.perform(get("/api/ai/screenings/my")
                        .header("X-User-Id", "S-STUDENT-LOOP-001")
                        .header("X-User-Role", "STUDENT")
                        .param("studentId", "S-OTHER-LOOP-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentId").value("S-STUDENT-LOOP-001"))
                .andExpect(jsonPath("$.data[0].deliveryId").value("D-STUDENT-LOOP-001"));
    }

    @Test
    void companyGetsEmptyArrayFromStudentScreeningFeedbackEndpoint() throws Exception {
        screenCandidate("C-COMPANY-LOOP-001", "D-COMPANY-LOOP-001", "S-COMPANY-LOOP-001", "J-COMPANY-LOOP-001");

        mockMvc.perform(get("/api/ai/screenings/my")
                        .header("X-User-Id", "C-COMPANY-LOOP-001")
                        .header("X-User-Role", "COMPANY")
                        .param("studentId", "S-COMPANY-LOOP-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
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

    @Test
    void interviewFeedbackUsesStudentHeaderBeforeRequestStudentId() throws Exception {
        mockMvc.perform(post("/api/ai/interview/feedback")
                        .header("X-User-Id", "S-GATEWAY-TRUST-001")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S-BODY-TRUST-001",
                                  "questionId": "IQ-GATEWAY-TRUST-001",
                                  "question": "How do you troubleshoot a slow API?",
                                  "answer": "I check logs, metrics, SQL plans and cache hit rates, then reproduce the issue with focused load testing.",
                                  "targetRole": "Java Backend Intern"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mocked").value(true));

        mockMvc.perform(get("/api/ai/interview/records")
                        .header("X-User-Id", "S-GATEWAY-TRUST-001")
                        .header("X-User-Role", "STUDENT")
                        .param("studentId", "S-BODY-TRUST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].studentId").value("S-GATEWAY-TRUST-001"))
                .andExpect(jsonPath("$.data[0].questionId").value("IQ-GATEWAY-TRUST-001"));

        mockMvc.perform(get("/api/ai/interview/records")
                        .param("studentId", "S-BODY-TRUST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void adminCanQueryInterviewRecordsByRequestStudentId() throws Exception {
        mockMvc.perform(post("/api/ai/interview/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "S-ADMIN-VIEW-001",
                                  "questionId": "IQ-ADMIN-VIEW-001",
                                  "question": "How do you optimize a database query?",
                                  "answer": "I inspect execution plans, add suitable indexes, reduce scanned rows and verify the change with metrics.",
                                  "targetRole": "Java Backend Intern"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ai/interview/records")
                        .header("X-User-Id", "A-ADMIN-001")
                        .header("X-User-Role", "ADMIN")
                        .param("studentId", "S-ADMIN-VIEW-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].studentId").value("S-ADMIN-VIEW-001"))
                .andExpect(jsonPath("$.data[0].questionId").value("IQ-ADMIN-VIEW-001"));
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
                                  "resumeSourceFormat": "PDF",
                                  "resumeParseStatus": "TEXT_EXTRACTED",
                                  "resumeParsedTextLength": 96,
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

    private String submitAsyncTask(String companyId, String deliveryId) throws Exception {
        String response = mockMvc.perform(post("/api/ai/candidates/screen/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "deliveryId": "%s",
                                  "studentId": "S-ASYNC-HELPER-001",
                                  "resumeId": "R-ASYNC-HELPER-001",
                                  "jobId": "J-ASYNC-HELPER-001",
                                  "resumeSourceFormat": "PDF",
                                  "resumeParseStatus": "TEXT_EXTRACTED",
                                  "resumeParsedTextLength": 128,
                                  "targetRole": "Java Backend Intern",
                                  "skills": ["Java", "Spring Boot"],
                                  "projects": ["Campus recruitment platform"],
                                  "jobRequirements": ["Java", "Spring Boot"],
                                  "resumeSummary": "Java backend project experience",
                                  "jobDescription": "Build backend APIs"
                                }
                                """.formatted(companyId, deliveryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.taskId");
    }

    private void waitUntilTaskCompleted(String companyId, String deliveryId) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            String content = mockMvc.perform(get("/api/ai/candidates/screen/tasks")
                            .param("companyId", companyId)
                            .param("deliveryId", deliveryId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (content.contains("\"status\":\"COMPLETED\"")) {
                return;
            }
            Thread.sleep(50);
        }
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
