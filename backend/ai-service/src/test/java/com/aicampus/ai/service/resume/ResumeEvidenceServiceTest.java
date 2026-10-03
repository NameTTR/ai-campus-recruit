package com.aicampus.ai.service.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.ai.controller.ResumeEvidenceController;
import com.aicampus.ai.service.DashScopeClient;
import com.aicampus.common.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;

class ResumeEvidenceServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DashScopeClient client = mock(DashScopeClient.class);
    private final ResumeEvidenceService service =
            new ResumeEvidenceService(client, mapper, "qwen-plus");

    @Test
    void modelQuotesAndRewritesAreValidatedWithOnlyOneCompletion() {
        when(client.complete(anyString(), anyString(), eq(true)))
                .thenReturn(
                        """
{"skillEvidence":[{"skill":"Spring Boot","quote":"使用 Spring Boot 提高吞吐 200%","supported":true}],
 "findings":[{"category":"EXPRESSION","originalQuote":"使用 Java 开发课程 API","issue":"缺少验证方式",
 "suggestedRewrite":"使用 Java 开发课程 API，提高性能 200%","basis":"岗位","requiredSkill":"Java"}]}
""");
        var diagnosis = service.analyze(request());
        assertThat(diagnosis.metadata().source()).isEqualTo("AI_STRUCTURED_EVIDENCE");
        assertThat(diagnosis.skillEvidence())
                .anyMatch(item -> item.skill().equals("Spring Boot") && !item.supported());
        assertThat(diagnosis.findings())
                .noneMatch(item -> item.suggestedRewrite().contains("200%"));
        verify(client, times(1)).complete(anyString(), anyString(), eq(true));
    }

    @Test
    void failureReturnsExplicitRulesAndNeverRetriesInsideOneAnalysis() {
        when(client.complete(anyString(), anyString(), eq(true)))
                .thenThrow(new IllegalStateException("temporary outage"));
        var diagnosis = service.analyze(request());
        assertThat(diagnosis.metadata().source()).isEqualTo("RULE_FALLBACK");
        verify(client, times(1)).complete(anyString(), anyString(), eq(true));
    }

    @Test
    void endpointRejectsAnotherStudentsSnapshotBeforeCallingModel() throws Exception {
        ResumeEvidenceService mocked = mock(ResumeEvidenceService.class);
        var controller = new ResumeEvidenceController(mocked, mapper);
        var body = new AiAnalyzeRequest("resume", "", mapper.writeValueAsString(request()));
        assertThat(controller.analyze(body, "S2", "STUDENT").code()).isNotZero();
        assertThat(controller.analyze(body, "S1", "COMPANY").code()).isNotZero();
        verifyNoInteractions(mocked);
    }

    private ResumeEvidenceRequest request() {
        return new ResumeEvidenceRequest(
                "R1",
                "S1",
                "Java engineer",
                null,
                new ResumeProfileSnapshot(
                        "Bachelor",
                        List.of("Java"),
                        List.of("使用 Java 开发课程 API"),
                        "Skills: Java\nProject: 使用 Java 开发课程 API"),
                "fingerprint");
    }
}
