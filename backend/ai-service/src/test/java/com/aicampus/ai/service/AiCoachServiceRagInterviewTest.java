package com.aicampus.ai.service;

import com.aicampus.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiCoachServiceRagInterviewTest {
    @Test void ruleGapQuestionsStayOnRedisInsteadOfGeneralJavaSkills() throws Exception {
        AiCoachService coach = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost"));
        List<InterviewQuestion> questions = coach.generateInterviewQuestions(scoped("GAP", "Redis", "岗位材料中尚未体现Redis", 4));
        assertThat(questions).hasSize(4).allSatisfy(q ->
                assertThat(q.question()).contains("Redis").doesNotContain("Spring Boot", "MySQL", "Java "));
    }

    @Test void ruleProjectQuestionsDoNotInventUnprovidedTechnologies() throws Exception {
        AiCoachService coach = new AiCoachService(new DashScopeClient("", "qwen-plus", "http://localhost"));
        assertThat(coach.generateInterviewQuestions(scoped("PROJECT", "社团报名页面", "我使用HTML和CSS制作报名页面", 3)))
                .hasSize(3).allSatisfy(q -> assertThat(q.question()).contains("社团报名页面").doesNotContain("Spring Boot", "Redis", "Java"));
    }

    @Test void offTopicModelQuestionFallsBackWithoutAnotherModelCall() throws Exception {
        DashScopeClient client = configured();
        when(client.complete(anyString(), anyString(), anyBoolean())).thenReturn(questionJson("Spring Boot三层架构有哪些职责？", "说明分层"));
        AiCoachService coach = new AiCoachService(client);
        InterviewQuestion q = coach.generateInterviewQuestions(scoped("GAP", "Redis", "需要补充Redis实践", 1)).get(0);
        assertThat(q.questionId()).startsWith("IQ-RAG-");
        assertThat(q.question()).contains("Redis").doesNotContain("Spring Boot");
        verify(client, times(1)).complete(anyString(), anyString(), anyBoolean());
    }

    @Test void validRedisQuestionAllowsRelatedRisksAndKeepsRealModelResult() throws Exception {
        DashScopeClient client = configured();
        when(client.complete(anyString(), anyString(), anyBoolean())).thenReturn(questionJson(
                "Redis缓存与数据库出现数据不一致时，如何分析风险和验证方案？", "说明Redis失效策略、数据库更新与测试"));
        AiCoachService coach = new AiCoachService(client);
        assertThat(coach.generateInterviewQuestions(scoped("GAP", "Redis", "需要补充Redis实践", 1)).get(0).questionId()).isEqualTo("IQ-AI-1");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).complete(anyString(), prompt.capture(), anyBoolean());
        assertThat(prompt.getValue()).contains("最高优先级的来源范围", "只围绕所选能力缺口", "Redis");
        assertThat(prompt.getValue()).doesNotContain("技能：Java");
    }

    @Test void projectResponseThatAssumesUnprovidedRedisFallsBack() throws Exception {
        DashScopeClient client = configured();
        when(client.complete(anyString(), anyString(), anyBoolean())).thenReturn(questionJson(
                "社团报名页面中你使用Redis和Kubernetes实现了什么？", "说明Redis缓存和Kubernetes部署"));
        assertThat(new AiCoachService(client).generateInterviewQuestions(scoped("PROJECT", "社团报名页面",
                "我使用HTML和CSS制作报名页面", 1)).get(0).questionId()).startsWith("IQ-RAG-");
        verify(client, times(1)).complete(anyString(), anyString(), anyBoolean());
    }

    @Test void fractionalLearningHoursPreserveMinuteBudgets() {
        DashScopeClient client = configured();
        when(client.complete(anyString(), anyString(), anyBoolean())).thenReturn(planJson(
                "\"estimatedHours\":0.5", "\"estimatedHours\":0.5", "\"estimatedHours\":1"));
        CareerPlanResponse response = new AiCoachService(client).careerPlan(planRequest());
        assertThat(response.mocked()).isFalse();
        assertThat(response.tasks()).extracting(CareerLearningTask::estimatedMinutes).containsExactly(30, 30, 60);
        assertThat(response.tasks().stream().mapToInt(CareerLearningTask::durationMinutes).sum()).isEqualTo(120);
        assertThat(response.tasks()).extracting(CareerLearningTask::estimatedHours).containsExactly(1, 1, 1);
    }

    @Test void explicitMinutesTakePrecedenceAndFractionalMinutesRoundUp() {
        DashScopeClient client = configured();
        when(client.complete(anyString(), anyString(), anyBoolean())).thenReturn(planJson(
                "\"estimatedMinutes\":15,\"estimatedHours\":1", "\"estimatedHours\":0.01"));
        CareerPlanResponse response = new AiCoachService(client).careerPlan(planRequest());
        assertThat(response.mocked()).isFalse();
        assertThat(response.tasks()).extracting(CareerLearningTask::estimatedMinutes).containsExactly(15, 1);
    }

    @Test void invalidLearningDurationsDoNotBecomeStructuredTasks() {
        for (String invalid : List.of("\"estimatedHours\":0", "\"estimatedHours\":-0.5", "\"estimatedHours\":\"0.5\"",
                "\"estimatedHours\":1000000000000", "\"estimatedMinutes\":0", "\"estimatedMinutes\":1.5", "\"estimatedMinutes\":2401")) {
            DashScopeClient client = configured();
            when(client.complete(anyString(), anyString(), anyBoolean())).thenReturn(planJson(invalid));
            assertThat(new AiCoachService(client).careerPlan(planRequest()).mocked()).as(invalid).isTrue();
        }
    }

    private static InterviewQuestionRequest scoped(String type, String focus, String material, int count) throws Exception {
        String source = new ObjectMapper().writeValueAsString(Map.of("sourceType", type, "sourceFocus", focus, "sourceMaterial", material));
        return new InterviewQuestionRequest("S", null, null, "Java后端实习生", "GAP".equals(type) ? List.of(focus) : List.of(),
                count, true, 6, source, "GAP".equals(type) ? List.of(focus) : List.of(), "GAP".equals(type) ? List.of(focus) : List.of());
    }
    private static DashScopeClient configured() {
        DashScopeClient client = mock(DashScopeClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.status()).thenReturn(new AiModuleStatus("dashscope", "qwen-plus", true, "http://localhost", List.of(), null));
        return client;
    }
    private static String questionJson(String question, String point) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("questions", List.of(Map.of("questionId", "IQ-AI-1", "category", "项目深挖",
                    "difficulty", "中等", "question", question, "referencePoints", List.of(point)))));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static CareerPlanRequest planRequest() {
        return new CareerPlanRequest("S", "Java", List.of(), List.of(), null, 1, 2, List.of("Java"));
    }
    private static String planJson(String... durations) {
        return "{\"summary\":\"分钟任务\",\"tasks\":[" + java.util.Arrays.stream(durations)
                .map(duration -> "{\"week\":1,\"title\":\"接口验证\",\"targetSkill\":\"Java\",\"prerequisites\":[],"
                        + duration + ",\"exercise\":\"实现接口并测试\",\"acceptanceCriteria\":\"测试通过\",\"deliverable\":\"测试记录\"}")
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }
}
