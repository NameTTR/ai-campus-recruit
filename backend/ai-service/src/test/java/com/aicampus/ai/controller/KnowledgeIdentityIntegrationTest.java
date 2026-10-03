package com.aicampus.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.ai.AiServiceApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AiServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false", "dashscope.api-key=",
        "ai.knowledge.seed.enabled=false"
})
@AutoConfigureMockMvc
class KnowledgeIdentityIntegrationTest {
    @Autowired private MockMvc mvc;

    @Test
    void cachesAreScopedToVerifiedUserAndBodyCannotElevateRole() throws Exception {
        mvc.perform(post("/api/ai/knowledge/documents")
                .header("X-User-Id", "A001").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"private-identity-marker\",\"content\":\"private-identity-marker Redis cache verification\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        String request = "{\"query\":\"private-identity-marker\",\"role\":\"ADMIN\",\"useAi\":false}";
        String first = mvc.perform(post("/api/ai/knowledge/answer")
                .header("X-User-Id", "S001").header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.citations").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/ai/knowledge/answer")
                .header("X-User-Id", "S002").header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.citations").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String key1 = JsonPath.read(first, "$.data.inputFingerprint");
        String key2 = JsonPath.read(second, "$.data.inputFingerprint");
        assertThat(key1).isNotBlank().isNotEqualTo(key2);
        mvc.perform(post("/api/ai/knowledge/search")
                .header("X-User-Id", "S001").header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.results").isEmpty());
        mvc.perform(post("/api/ai/knowledge/answer")
                .header("X-User-Id", "A001").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.citations[0].documentId").isNotEmpty());
    }
}
