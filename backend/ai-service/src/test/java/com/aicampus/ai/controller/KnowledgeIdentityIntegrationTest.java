package com.aicampus.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void studentReadsStableRevisionThatChangesWhenDocumentsOrPermissionsChange() throws Exception {
        String initialRevision = knowledgeRevision();
        assertThat(initialRevision).isNotBlank().isEqualTo(knowledgeRevision());

        String created = mvc.perform(post("/api/ai/knowledge/documents")
                .header("X-User-Id", "A001").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"revision-marker\",\"content\":\"An authorized learning reference.\",\"roles\":[\"STUDENT\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(created, "$.data.documentId");
        try {
            String createdRevision = knowledgeRevision();
            assertThat(createdRevision).isNotEqualTo(initialRevision).isEqualTo(knowledgeRevision());

            mvc.perform(patch("/api/ai/knowledge/documents/{documentId}/roles", documentId)
                    .header("X-User-Id", "A001").header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            String restrictedRevision = knowledgeRevision();
            assertThat(restrictedRevision).isNotEqualTo(createdRevision).isEqualTo(knowledgeRevision());

            mvc.perform(delete("/api/ai/knowledge/documents/{documentId}", documentId)
                    .header("X-User-Id", "A001").header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            assertThat(knowledgeRevision()).isNotEqualTo(restrictedRevision).isEqualTo(initialRevision);
        } finally {
            mvc.perform(delete("/api/ai/knowledge/documents/{documentId}", documentId)
                    .header("X-User-Id", "A001").header("X-User-Role", "ADMIN"));
        }
    }

    private String knowledgeRevision() throws Exception {
        String response = mvc.perform(get("/api/ai/knowledge/revision")
                .header("X-User-Id", "S001").header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data");
    }

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
