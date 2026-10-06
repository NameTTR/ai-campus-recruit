package com.aicampus.ai.service.knowledge.workspace;

import static com.aicampus.common.dto.KnowledgeWorkspaceModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicampus.ai.service.DashScopeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgePracticeEvaluatorTest {
    private final DashScopeClient client = mock(DashScopeClient.class);
    private final KnowledgePracticeEvaluator evaluator = new KnowledgePracticeEvaluator(client, new ObjectMapper());
    private final KnowledgePracticeQuestion question = new KnowledgePracticeQuestion("q", 1, "UNDERSTANDING", "Explain Redis cache invalidation",
            List.of("chunk"), List.of("Explain invalidation strategy"));
    private final List<KnowledgeSourceLocation> locations = List.of(new KnowledgeSourceLocation("chunk", 0, "Cache", 0, 10, null, "Invalidate after data changes"));

    @Test void validatesAnswerQuotesAndAuthorizedReferences() {
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn("{\"score\":75,\"judgement\":\"SUPPORTED\",\"quote\":\"invalidate\",\"feedback\":\"Explained strategy\",\"nextAction\":\"Add a race test\",\"referenceChunkIds\":[\"chunk\"]}");
        KnowledgePracticeEvaluation result = evaluator.evaluate(question, "I invalidate after update", locations);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.referenceChunkIds()).containsExactly("chunk");
        verify(client, times(1)).complete(anyString(), anyString(), eq(true));
    }

    @Test void fabricatedAnswerQuoteFails() {
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn("{\"score\":90,\"judgement\":\"SUPPORTED\",\"quote\":\"never said this\",\"feedback\":\"ok\",\"nextAction\":\"test\",\"referenceChunkIds\":[\"chunk\"]}");
        assertThatThrownBy(() -> evaluator.evaluate(question, "saved answer", locations)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void fabricatedSourceCitationFails() {
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString(), anyString(), eq(true))).thenReturn("{\"score\":90,\"judgement\":\"SUPPORTED\",\"quote\":\"answer\",\"feedback\":\"ok\",\"nextAction\":\"test\",\"referenceChunkIds\":[\"private-chunk\"]}");
        assertThatThrownBy(() -> evaluator.evaluate(question, "saved answer", locations)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void providerFailureIsNotReportedAsSuccessfulEvaluation() {
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString(), anyString(), eq(true))).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> evaluator.evaluate(question, "saved answer", locations)).isInstanceOf(IllegalStateException.class);
    }

    @Test void ruleModeDoesNotClaimMasteryOrAssignInventedScore() {
        KnowledgePracticeEvaluation result = evaluator.evaluate(question, "saved answer", locations);
        assertThat(result.status()).isEqualTo("DEMO");
        assertThat(result.judgement()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(result.score()).isZero();
        verify(client, never()).complete(anyString(), anyString(), anyBoolean());
    }
}
