package com.aicampus.resume.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.*;
import com.aicampus.common.evidence.*;
import com.aicampus.resume.client.*;
import com.aicampus.resume.service.*;
import com.aicampus.resume.service.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class ResumeEvidenceFlowTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AiAnalyzeClient ai = mock(AiAnalyzeClient.class);
    private final ResumeJobClient jobs = mock(ResumeJobClient.class);
    private final InMemoryResumeRecordStore store = new InMemoryResumeRecordStore();
    private final ResumeController controller =
            new ResumeController(
                    ai,
                    mock(ResumeObjectStorageService.class),
                    mock(ResumeTextExtractionService.class),
                    store,
                    false);

    private void prepare() {
        controller.setJobClient(jobs);
        ResumeSummary resume =
                new ResumeSummary(
                        "R1",
                        "S1",
                        "resume.docx",
                        "Bachelor",
                        List.of("Java"),
                        List.of("使用 Java 开发 API 并测试"),
                        "old",
                        45,
                        "",
                        "",
                        "",
                        "DOCX",
                        "TEXT_EXTRACTED",
                        50);
        store.save(new ResumeRecord(resume, "Bachelor\nSkills: Java\nProject: 使用 Java 开发 API 并测试"));
        when(jobs.detail(anyString(), anyString(), anyString()))
                .thenReturn(
                        ApiResponse.ok(
                                new JobSummary(
                                        "J1",
                                        "C1",
                                        "Company",
                                        "Actual frontend",
                                        "Shanghai",
                                        "",
                                        List.of("JavaScript"),
                                        "Bachelor required",
                                        "")));
        when(ai.analyze(any()))
                .thenAnswer(
                        invocation -> {
                            AiAnalyzeRequest call = invocation.getArgument(0);
                            ResumeEvidenceRequest request =
                                    mapper.readValue(call.context(), ResumeEvidenceRequest.class);
                            StructuredResumeDiagnosis result =
                                    ResumeEvidenceRules.baseline(
                                            store.findById("R1").orElseThrow().summary(),
                                            request.profile().resumeText(),
                                            request.targetJob(),
                                            request.job(),
                                            request.inputFingerprint(),
                                            "AI_STRUCTURED_EVIDENCE",
                                            "qwen-plus");
                            return ApiResponse.ok(
                                    new AiAnalyzeResponse(
                                            "resume",
                                            "dashscope",
                                            mapper.writeValueAsString(result),
                                            false));
                        });
    }

    @Test
    void actualJobOverridesClientTitleAndSuccessfulResultIsReused() {
        prepare();
        var request = new ResumeAnalyzeRequest("R1", "forged", "Java", "J1");
        var first = controller.analyze("R1", request, "S1", "STUDENT").data();
        var second = controller.analyze("R1", request, "S1", "STUDENT").data();
        assertThat(first.structuredDiagnosis().jobSnapshot().title()).isEqualTo("Actual frontend");
        assertThat(first.structuredDiagnosis().profileSnapshot().resumeText())
                .doesNotContain("forged");
        assertThat(second.structuredDiagnosis().metadata().inputFingerprint())
                .isEqualTo(first.structuredDiagnosis().metadata().inputFingerprint());
        verify(ai, times(1)).analyze(any());
        assertThat(store.findById("R1").orElseThrow().diagnoses()).hasSize(1);
    }

    @Test
    void concurrentIdenticalRequestsSaveAndChargeOnlyOneAnalysis() throws Exception {
        prepare();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var request = new ResumeAnalyzeRequest("R1", "", "Java", "J1");
            var left = executor.submit(() -> controller.analyze("R1", request, "S1", "STUDENT"));
            var right = executor.submit(() -> controller.analyze("R1", request, "S1", "STUDENT"));
            assertThat(left.get(5, TimeUnit.SECONDS).code()).isZero();
            assertThat(right.get(5, TimeUnit.SECONDS).code()).isZero();
            verify(ai, times(1)).analyze(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void profileChangeMarksSummaryAndImmutableHistoryStale() {
        prepare();
        controller.analyze("R1", new ResumeAnalyzeRequest("R1", "", "Java", "J1"), "S1", "STUDENT");
        controller.updateProfile(
                "R1",
                new ResumeProfileUpdateRequest(null, List.of("TypeScript"), null),
                "S1",
                "STUDENT");
        assertThat(controller.detail("R1", "S1", "STUDENT").data().structuredDiagnosis().stale())
                .isTrue();
        var history = controller.diagnoses("R1", "S1", "STUDENT").data().get(0);
        assertThat(history.details().stale()).isTrue();
        assertThat(history.skillsSnapshot()).containsExactly("Java");
        assertThat(controller.detail("R1", "S2", "STUDENT").code()).isNotZero();
    }

    @Test
    void analysisDoesNotOverwriteProfileChangedWhileAiWasRunning() {
        prepare();
        doAnswer(
                        invocation -> {
                            controller.updateProfile(
                                    "R1",
                                    new ResumeProfileUpdateRequest(
                                            null, List.of("TypeScript"), null),
                                    "S1",
                                    "STUDENT");
                            return successfulAiResponse(invocation);
                        })
                .when(ai)
                .analyze(any());

        var response =
                controller.analyze(
                        "R1", new ResumeAnalyzeRequest("R1", "", "Java", "J1"), "S1", "STUDENT");

        assertThat(response.code()).isNotZero();
        assertThat(response.message()).contains("资料已在诊断期间更新");
        assertThat(store.findById("R1").orElseThrow().summary().skills())
                .containsExactly("TypeScript");
        assertThat(store.findById("R1").orElseThrow().diagnoses()).isEmpty();
    }

    @Test
    void analysisDoesNotRecreateResumeDeletedWhileAiWasRunning() {
        prepare();
        doAnswer(
                        invocation -> {
                            store.delete("R1");
                            return successfulAiResponse(invocation);
                        })
                .when(ai)
                .analyze(any());

        var response =
                controller.analyze(
                        "R1", new ResumeAnalyzeRequest("R1", "", "Java", "J1"), "S1", "STUDENT");

        assertThat(response.code()).isNotZero();
        assertThat(response.message()).contains("诊断期间删除");
        assertThat(store.findById("R1")).isEmpty();
    }

    private ApiResponse<AiAnalyzeResponse> successfulAiResponse(
            org.mockito.invocation.InvocationOnMock invocation) throws Exception {
        AiAnalyzeRequest call = invocation.getArgument(0);
        ResumeEvidenceRequest request =
                mapper.readValue(call.context(), ResumeEvidenceRequest.class);
        StructuredResumeDiagnosis result =
                ResumeEvidenceRules.baseline(
                        new ResumeSummary(
                                "R1",
                                "S1",
                                "resume.docx",
                                request.profile().education(),
                                request.profile().skills(),
                                request.profile().projects(),
                                "",
                                0,
                                "",
                                "",
                                "",
                                "DOCX",
                                "TEXT_EXTRACTED",
                                request.profile().resumeText().length()),
                        request.profile().resumeText(),
                        request.targetJob(),
                        request.job(),
                        request.inputFingerprint(),
                        "AI_STRUCTURED_EVIDENCE",
                        "qwen-plus");
        return ApiResponse.ok(
                new AiAnalyzeResponse(
                        "resume", "dashscope", mapper.writeValueAsString(result), false));
    }

    @Test
    void snapshotsPersistAndOldJsonRemainsReadable() throws Exception {
        prepare();
        controller.analyze("R1", new ResumeAnalyzeRequest("R1", "", "Java", "J1"), "S1", "STUDENT");
        var record = store.findById("R1").orElseThrow();
        var entity = ResumeRecordEntity.fromRecord(record, mapper);
        assertThat(entity.toRecord(mapper)).isEqualTo(record);
        entity.setStructuredDiagnosis(null);
        var oldHistory = mapper.valueToTree(record.diagnoses());
        oldHistory.forEach(
                node -> ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("details"));
        entity.setDiagnosisHistory(mapper.writeValueAsString(oldHistory));
        assertThat(entity.toRecord(mapper).summary().structuredDiagnosis()).isNull();
        assertThat(entity.toRecord(mapper).diagnoses().get(0).details()).isNull();
    }
}
