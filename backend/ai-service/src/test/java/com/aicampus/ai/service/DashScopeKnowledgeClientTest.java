package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.aicampus.ai.service.knowledge.DashScopeKnowledgeClient;
import com.aicampus.ai.service.knowledge.KnowledgeBaseProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DashScopeKnowledgeClientTest {
    private static final String URL = "http://provider.test";
    private KnowledgeBaseProperties properties() {
        KnowledgeBaseProperties props = new KnowledgeBaseProperties();
        props.getSemantic().setDimension(2);
        props.getSemantic().setEmbeddingUrl(URL + "/embedding");
        props.getSemantic().setRerankUrl(URL + "/rerank");
        return props;
    }
    private DashScopeClient client(RestClient client) {
        return new DashScopeClient("fake-unit-test-key", "qwen-plus", URL, 0.2, 100,
                Duration.ofSeconds(1), Duration.ofSeconds(2), 2, Duration.ofSeconds(1), 1, Duration.ofSeconds(30), client);
    }
    @Test void sendsNativeEmbeddingParametersAndOrdersReturnedVectorsByTextIndex() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL + "/embedding"))
                .andExpect(jsonPath("$.model").value("text-embedding-v4"))
                .andExpect(jsonPath("$.parameters.dimension").value(2))
                .andExpect(jsonPath("$.parameters.text_type").value("document"))
                .andExpect(jsonPath("$.input.texts[0]").value("Java"))
                .andRespond(withSuccess("{\"output\":{\"embeddings\":[{\"text_index\":1,\"embedding\":[0.0,1.0]},{\"text_index\":0,\"embedding\":[1.0,0.0]}]}}",MediaType.APPLICATION_JSON));
        DashScopeKnowledgeClient semantic = new DashScopeKnowledgeClient(client(builder.build()), properties());
        assertThat(semantic.embed(List.of("Java","Redis"),false)).containsExactly(List.of(1.0,0.0),List.of(0.0,1.0));
        server.verify();
    }
    @Test void sendsNativeGteRerankFormatAndDecodesScores() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL + "/rerank"))
                .andExpect(jsonPath("$.model").value("gte-rerank-v2"))
                .andExpect(jsonPath("$.input.query").value("Redis"))
                .andExpect(jsonPath("$.parameters.top_n").value(2))
                .andRespond(withSuccess("{\"output\":{\"results\":[{\"index\":1,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.2}]}}",MediaType.APPLICATION_JSON));
        DashScopeKnowledgeClient semantic = new DashScopeKnowledgeClient(client(builder.build()), properties());
        assertThat(semantic.rerank("Redis",List.of("Java","Redis"))).containsExactly(
                new DashScopeKnowledgeClient.RerankResult(1,0.9),new DashScopeKnowledgeClient.RerankResult(0,0.2));
        server.verify();
    }
    @Test void badEmbeddingDimensionOpensTheSameCircuitUsedByChat() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL + "/embedding"))
                .andRespond(withSuccess("{\"output\":{\"embeddings\":[{\"text_index\":0,\"embedding\":[1.0]}]}}",MediaType.APPLICATION_JSON));
        DashScopeClient guard = client(builder.build());
        DashScopeKnowledgeClient semantic = new DashScopeKnowledgeClient(guard,properties());
        assertThatThrownBy(() -> semantic.embed(List.of("Java"),true)).hasMessageContaining("dimension");
        assertThatThrownBy(() -> guard.complete("system","user",true)).hasMessageContaining("circuit is open");
        server.verify();
    }
    @Test void unsupportedIndexAndNonFiniteEmbeddingValuesAreRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL + "/embedding"))
                .andRespond(withSuccess("{\"output\":{\"embeddings\":[{\"text_index\":9,\"embedding\":[1.0,0.0]}]}}",MediaType.APPLICATION_JSON));
        DashScopeKnowledgeClient semantic = new DashScopeKnowledgeClient(client(builder.build()),properties());
        assertThatThrownBy(() -> semantic.embed(List.of("Java"),true)).hasMessageContaining("index");
        server.verify();
    }
}
