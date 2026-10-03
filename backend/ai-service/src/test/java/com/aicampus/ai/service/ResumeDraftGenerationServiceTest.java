package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aicampus.common.dto.AiModuleStatus;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResumeDraftGenerationServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final SourceRef CONFIRMED = new SourceRef("USER", "p1", "学生确认", true, "CONFIRMED");
    private static final SourceRef UNCONFIRMED = new SourceRef("IMPORT", "p1", "导入待确认", false, "UNCONFIRMED");
    private final ResumeDraftGenerationService rules = new ResumeDraftGenerationService(
            new DashScopeClient("", "qwen-plus", "http://localhost"));

    @Test
    void factsWithoutConfirmationSourceNeverEnterDraftOrModelContext() {
        var raw = new ProfileData(emptyProfile().basics(),
                List.of(new Education("edu", "学生自填大学", "计算机", "本科", "", "", "", List.of(), "", null)),
                List.of(new SkillItem("skill", "Java", null)),
                List.of(new Experience("exp", "PROJECT", "课程项目", "", "", "", "", "使用 Java 实现接口", "", "", List.of("Java"), List.of(), null, true)),
                List.of(new Credential("award", "竞赛奖", "", "获奖", null)), null);
        DraftData result = rules.generate(request(raw, "Java", null, "x"));
        assertThat(result.blocks()).isEmpty();
        assertThat(result.generationSource()).startsWith("RULES:");
    }

    @Test
    void emptyStudentProducesNoInventedExperienceOrSkillsAndDoesNotCallProvider() {
        DashScopeClient client = configuredClient();
        ResumeDraftGenerationService service = new ResumeDraftGenerationService(client);
        DraftData result = service.generate(request(emptyProfile(), "Java 后端", null, "arbitrary"));
        assertThat(result.blocks()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.warnings()).anyMatch(value -> value.contains("经历"));
        assertThat(result.generationSource()).startsWith("RULES_NO_CONFIRMED_CONTENT:");
        verify(client, never()).complete(anyString(), anyString(), eq(true));
    }

    @Test
    void suppliedFactsRemainInBaseAndMissingResultsProduceOneQuestion() {
        ProfileData profile = profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", ""));
        DraftData result = rules.generate(request(profile, "Java 后端", null, "facts-v1"));
        DraftEntry entry = entry(result, "p1");
        assertThat(entry.bullets()).containsExactly("实现登录接口", "使用 Java");
        assertThat(entry.factIds()).containsExactly("p1");
        assertThat(String.join(" ", entry.bullets())).doesNotContain("Redis", "99%", "提升");
        assertThat(result.questions()).filteredOn(item -> item.factId().equals("p1")).hasSize(1);
        assertThat(result.generationSource()).startsWith("RULES:");
    }

    @Test
    void eachWeakExperienceGetsAtMostThreeQuestionsInsteadOfExportedPlaceholder() {
        DraftData result = rules.generate(request(profile(experience("p1", "PROJECT", "", "", "")), "运营", null, "a"));
        assertThat(result.questions()).hasSize(3);
        assertThat(entry(result, "p1").bullets()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void modelUsesRealDashScopeJsonPipelineAndNeverModifiesConfirmedBaseFacts() throws Exception {
        String provider = "http://provider.test";
        RestClient.Builder builder = RestClient.builder().baseUrl(provider);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String output = response(List.of(proposal("project", "p1", "实现登录接口", List.of("p1"),
                "使用 Java，实现登录接口", List.of("使用 Java", "实现登录接口"))));
        String response = MAPPER.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", output)))));
        server.expect(requestTo(provider + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer fake-test-key"))
                .andExpect(jsonPath("$.model").value("qwen-test"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        DashScopeClient client = new DashScopeClient("fake-test-key", "qwen-test", provider, 0.2, 512,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ofSeconds(1),
                3, Duration.ofSeconds(30), builder.build());
        ResumeDraftGenerationService service = new ResumeDraftGenerationService(client);
        DraftGenerationRequest request = request(profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", "")),
                "Java 后端", job("j1", "Java 后端", "Java required"), "ignored");
        DraftData result = service.generate(request);
        assertThat(result.generationSource()).startsWith("AI_DASHSCOPE:").contains("qwen-test");
        assertThat(entry(result, "p1").bullets()).containsExactly("实现登录接口", "使用 Java");
        assertThat(result.suggestions()).hasSize(1);
        DraftSuggestion suggestion = result.suggestions().get(0);
        assertThat(suggestion.factIds()).containsExactly("p1");
        assertThat(suggestion.status()).isEqualTo("UNCONFIRMED");
        assertThat(suggestion.originalQuote()).isEqualTo("实现登录接口");
        assertThat(suggestion.suggestedText()).isEqualTo("使用 Java，实现登录接口");
        assertThat(service.generate(request)).isSameAs(result);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"使用 Java，实现登录接口，提升性能 99%", "使用 Redis，实现登录接口",
            "担任负责人，实现登录接口", "在字节跳动实现登录接口", "设计分布式架构并实现登录接口",
            "使用 SuperUnknownFramework，实现登录接口", "使用 Java，实现登录接口，达到企业级水平"})
    void rejectsInventedMetricsTechnologyResponsibilityOrganizationOrClaims(String invented) throws Exception {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn(response(List.of(
                proposal("project", "p1", "实现登录接口", List.of("p1"), invented, List.of("实现登录接口", "使用 Java")))));
        DraftData result = new ResumeDraftGenerationService(client).generate(request(
                profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", "")), "Java 后端", null, "x"));
        assertThat(result.suggestions()).isEmpty();
        assertThat(entry(result, "p1").bullets()).containsExactly("实现登录接口", "使用 Java");
        assertThat(result.warnings()).anyMatch(value -> value.contains("未通过事实或引用校验"));
    }

    @Test
    void detectsUnitChangesEvenWhenTheNumberItselfWasSupplied() throws Exception {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn(response(List.of(
                proposal("project", "p1", "完成 10 次测试", List.of("p1"), "完成 10% 次测试", List.of("完成 10 次测试")))));
        DraftData result = new ResumeDraftGenerationService(client).generate(request(
                profile(experience("p1", "PROJECT", "完成 10 次测试", "", "")), "前端", null, "x"));
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void changedResponsibilitiesOrNegationCannotBeSmuggledThroughShortQuotes() throws Exception {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn(response(List.of(
                proposal("project", "p1", "参与页面测试", List.of("p1"), "负责页面测试", List.of("页面测试")),
                proposal("project", "p1", "未使用 Redis", List.of("p1"), "使用 Redis", List.of("使用 Redis")))));
        DraftData result = new ResumeDraftGenerationService(client).generate(request(
                profile(experience("p1", "PROJECT", "参与页面测试", "未使用 Redis", "")), "前端", null, "x"));
        assertThat(result.suggestions()).isEmpty();
        assertThat(entry(result, "p1").bullets()).containsExactly("参与页面测试", "未使用 Redis");
    }

    @Test
    void validatesEverySentenceAndRejectsTheWholeProposalWhenOneSentenceInventsFacts() throws Exception {
        DashScopeClient client = configuredClient();
        Map<String, Object> proposal = proposal("project", "p1", "实现登录接口", List.of("p1"),
                "使用 Java，实现登录接口", List.of("使用 Java", "实现登录接口"));
        proposal.put("sentences", List.of(
                Map.of("text", "使用 Java，实现登录接口", "sourceQuotes", List.of("使用 Java", "实现登录接口")),
                Map.of("text", "性能提升 50%", "sourceQuotes", List.of("使用 Java"))));
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn(response(List.of(proposal)));
        DraftData result = new ResumeDraftGenerationService(client).generate(request(
                profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", "")), "Java", null, "x"));
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void forgedFactIdsUnknownEntriesAndFalseOriginalQuotesCannotBeApplied() throws Exception {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn(response(List.of(
                proposal("project", "p1", "实现登录接口", List.of("someone-else"), "使用 Java，实现登录接口", List.of("使用 Java", "实现登录接口")),
                proposal("project", "forged-entry", "实现登录接口", List.of("p1"), "实现登录接口", List.of("实现登录接口")),
                proposal("project", "p1", "原文没有这句话", List.of("p1"), "实现登录接口", List.of("实现登录接口")))));
        DraftData result = new ResumeDraftGenerationService(client).generate(request(
                profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", "")), "Java", null, "x"));
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void aSkillDeclaredElsewhereCannotBecomeProjectEvidence() throws Exception {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn(response(List.of(
                proposal("project", "p1", "实现登录接口", List.of("p1", "redis-skill"), "使用 Redis，实现登录接口", List.of("Redis", "实现登录接口")))));
        ProfileData base = profile(experience("p1", "PROJECT", "实现登录接口", "", ""));
        ProfileData profile = new ProfileData(base.basics(), base.education(),
                List.of(new SkillItem("redis-skill", "Redis", CONFIRMED)), base.experiences(), base.credentials(), base.availability());
        DraftData result = new ResumeDraftGenerationService(client).generate(request(profile, "Java", null, "x"));
        assertThat(result.suggestions()).isEmpty();
        assertThat(entry(result, "p1").bullets()).doesNotContain("Redis");
    }

    @Test
    void excludesImportedOrUnconfirmedFactsFromTheModelPrompt() throws Exception {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn("{\"suggestions\":[]}");
        Experience unconfirmed = new Experience("secret", "INTERNSHIP", "尚未确认的实习", "未确认单位", "", "", "", "完成 999 次成果", "", "", List.of("SecretTech"), List.of(), UNCONFIRMED, false);
        ProfileData base = profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", ""));
        ProfileData profile = new ProfileData(base.basics(), List.of(), List.of(),
                List.of(base.experiences().get(0), unconfirmed), List.of(), base.availability());
        DraftData result = new ResumeDraftGenerationService(client).generate(request(profile, "Java", null, "x"));
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(client).complete(anyString(), input.capture(), eq(true));
        assertThat(input.getValue()).doesNotContain("SecretTech", "999", "未确认单位");
        assertThat(result.blocks()).flatExtracting(DraftBlock::entries).noneMatch(item -> item.id().equals("secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java 后端", "前端工程师", "运营实习生"})
    void threeJobFamiliesAllUseOneRealModelOrganizationCall(String role) throws Exception {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn("{\"suggestions\":[]}");
        Experience project = experience("p1", "PROJECT", "完成课程项目", "说明采用方法", "提交作品");
        Experience campus = experience("c1", "CAMPUS", "组织校园活动", "说明活动流程", "提交复盘");
        Experience internship = experience("i1", "INTERNSHIP", "整理实践记录", "说明工作流程", "交付记录");
        DraftData result = new ResumeDraftGenerationService(client).generate(request(profile(project, campus, internship), role,
                job("j1", role, "实际岗位原文"), "irrelevant"));
        assertThat(result.generationSource()).startsWith("AI_DASHSCOPE:");
        List<String> types = result.blocks().stream().filter(block -> !block.type().equals("SKILLS")).map(DraftBlock::type).toList();
        assertThat(types.get(0)).isEqualTo(role.contains("运营") ? "INTERNSHIP" : "PROJECT");
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).complete(anyString(), input.capture(), eq(true));
        JsonNode parsed = MAPPER.readTree(input.getValue());
        assertThat(parsed.path("targetRole").asText()).isEqualTo(role);
        assertThat(parsed.path("job").path("description").asText()).isEqualTo("实际岗位原文");
    }

    @Test
    void simultaneousSameInputPerformsOnePaidCallRegardlessOfCallerFingerprint() throws Exception {
        DashScopeClient client = configuredClient();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch providerReleased = new CountDownLatch(1);
        when(client.complete(anyString(), anyString(), eq(true))).thenAnswer(invocation -> {
            providerStarted.countDown();
            if (!providerReleased.await(5, TimeUnit.SECONDS)) throw new AssertionError("Provider latch timed out");
            return "{\"suggestions\":[]}";
        });
        ResumeDraftGenerationService service = new ResumeDraftGenerationService(client);
        ProfileData profile = profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", ""));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<DraftData>> requests = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String forgedFingerprint = "caller-" + i;
                requests.add(pool.submit(() -> service.generate(request(profile, "Java", null, forgedFingerprint))));
            }
            assertThat(providerStarted.await(3, TimeUnit.SECONDS)).isTrue();
            providerReleased.countDown();
            DraftData first = requests.get(0).get(5, TimeUnit.SECONDS);
            for (Future<DraftData> task : requests) assertThat(task.get(5, TimeUnit.SECONDS)).isSameAs(first);
            verify(client, times(1)).complete(anyString(), anyString(), eq(true));
        } finally {
            providerReleased.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void serverFingerprintIncludesUserFullProfileJobSnapshotRoleAndModel() {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn("{\"suggestions\":[]}");
        AtomicReference<String> model = new AtomicReference<>("qwen-plus");
        when(client.status()).thenAnswer(invocation -> status(model.get()));
        ResumeDraftGenerationService service = new ResumeDraftGenerationService(client);
        ProfileData profile = profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", ""));
        JobSummary job = job("j1", "Java", "Java required");
        DraftGenerationRequest request = request(profile, "Java", job, "same-untrusted-key");
        DraftData initial = service.generate(request);
        assertThat(service.generate(request(profile, "Java", job, "different-untrusted-key"))).isSameAs(initial);
        service.generate(new DraftGenerationRequest("another-user", profile, "Java", job, "same-untrusted-key"));
        ProfileData changedBasic = new ProfileData(new BasicInfo("B", "", "", "", "", null), profile.education(), profile.skills(), profile.experiences(), profile.credentials(), profile.availability());
        service.generate(request(changedBasic, "Java", job, "same-untrusted-key"));
        service.generate(request(profile, "Java", job("j1", "Java", "Redis is now required"), "same-untrusted-key"));
        service.generate(request(profile, "前端", job, "same-untrusted-key"));
        model.set("qwen-new");
        service.generate(request);
        verify(client, times(6)).complete(anyString(), anyString(), eq(true));
    }

    @Test
    void modelFailureKeepsOriginalsUsesExplicitFallbackAndConcurrentRetryCooldown() {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenThrow(new IllegalStateException("provider error containing private data"));
        ResumeDraftGenerationService service = new ResumeDraftGenerationService(client);
        DraftGenerationRequest request = request(profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", "")), "Java", null, "x");
        DraftData fallback = service.generate(request);
        assertThat(fallback.generationSource()).startsWith("RULE_FALLBACK:");
        assertThat(fallback.suggestions()).isEmpty();
        assertThat(entry(fallback, "p1").bullets()).containsExactly("实现登录接口", "使用 Java");
        assertThat(fallback.warnings()).anyMatch(value -> value.contains("暂时失败")).noneMatch(value -> value.contains("private data"));
        assertThat(service.generate(request)).isSameAs(fallback);
        verify(client, times(1)).complete(anyString(), anyString(), eq(true));
    }

    @Test
    void invalidModelJsonFallsBackAndMissingFactIdsNeverGetInvented() {
        DashScopeClient client = configuredClient();
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn("not json");
        DraftData fallback = new ResumeDraftGenerationService(client).generate(request(
                profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", "")), "Java", null, "x"));
        assertThat(fallback.generationSource()).startsWith("RULE_FALLBACK:");
        Experience missingId = experience("", "PROJECT", "真实工作原句", "", "");
        ProfileData noIds = new ProfileData(emptyProfile().basics(), List.of(), List.of(), List.of(missingId), List.of(), null);
        DraftData result = new ResumeDraftGenerationService(client).generate(request(noIds, "Java", null, "x"));
        assertThat(result.blocks()).flatExtracting(DraftBlock::entries).allSatisfy(entry -> assertThat(entry.factIds()).isEmpty());
        verify(client, times(1)).complete(anyString(), anyString(), eq(true));
    }

    @Test
    void flagCanDisableGenerationWithoutChangingFactsOrCallingDashScope() {
        DashScopeClient client = configuredClient();
        ResumeDraftGenerationService service = new ResumeDraftGenerationService(client);
        ReflectionTestUtils.setField(service, "modelEnabled", false);
        DraftData result = service.generate(request(profile(experience("p1", "PROJECT", "实现登录接口", "使用 Java", "")), "Java", null, "x"));
        assertThat(result.generationSource()).startsWith("RULES:");
        assertThat(entry(result, "p1").bullets()).containsExactly("实现登录接口", "使用 Java");
        verify(client, never()).complete(anyString(), anyString(), eq(true));
    }

    private static DashScopeClient configuredClient() {
        DashScopeClient client = mock(DashScopeClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.status()).thenReturn(status("qwen-plus"));
        return client;
    }
    private static AiModuleStatus status(String model) {
        return new AiModuleStatus("dashscope", model, true, "http://provider.test", List.of("resume-rewrite"), null);
    }
    private static ProfileData emptyProfile() {
        return new ProfileData(new BasicInfo("A", "", "", "", "", null), List.of(), List.of(), List.of(), List.of(),
                new Availability(List.of(), "", null, null, ""));
    }
    private static ProfileData profile(Experience... experiences) {
        return new ProfileData(emptyProfile().basics(), List.of(), List.of(new SkillItem("java-skill", "Java", CONFIRMED)),
                List.of(experiences), List.of(), emptyProfile().availability());
    }
    private static Experience experience(String id, String type, String actions, String methods, String results) {
        return new Experience(id, type, "课程实践", "学校课程", "", "", "成员", actions, methods, results,
                List.of(), List.of(), CONFIRMED, true);
    }
    private static DraftGenerationRequest request(ProfileData profile, String role, JobSummary job, String fingerprint) {
        return new DraftGenerationRequest("student", profile, role, job, fingerprint);
    }
    private static JobSummary job(String id, String title, String description) {
        return new JobSummary(id, "c1", "company", title, "Shanghai", "", List.of("Java"), description, "", "OPEN");
    }
    private static DraftEntry entry(DraftData data, String id) {
        return data.blocks().stream().flatMap(block -> block.entries().stream()).filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }
    private static Map<String, Object> proposal(String block, String entry, String original, List<String> factIds,
            String text, List<String> sourceQuotes) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("blockId", block);
        result.put("entryId", entry);
        result.put("originalQuote", original);
        result.put("factIds", factIds);
        result.put("sentences", List.of(Map.of("text", text, "sourceQuotes", sourceQuotes)));
        return result;
    }
    private static String response(List<Map<String, Object>> suggestions) throws Exception {
        return MAPPER.writeValueAsString(Map.of("suggestions", suggestions));
    }
}
