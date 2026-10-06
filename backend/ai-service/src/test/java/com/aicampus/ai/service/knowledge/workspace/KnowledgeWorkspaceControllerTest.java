package com.aicampus.ai.service.knowledge.workspace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import com.aicampus.ai.controller.AiApiExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;

class KnowledgeWorkspaceControllerTest {
    private final KnowledgeWorkspaceService workspace = mock(KnowledgeWorkspaceService.class);
    private final KnowledgeCatalogService catalog = mock(KnowledgeCatalogService.class);
    private final KnowledgeWorkspaceController controller = new KnowledgeWorkspaceController(workspace, catalog);

    @Test void originalSupportsFullSuffixAndBoundedByteRanges() {
        byte[] bytes = "%PDF-1.4 source file".getBytes(StandardCharsets.US_ASCII);
        when(catalog.readOriginal("doc", "STUDENT")).thenReturn(new KnowledgeCatalogService.OriginalFile(bytes, "application/pdf", "resume.pdf"));
        var full = controller.original("doc", "STUDENT", "student", null);
        assertThat(full.getStatusCode().value()).isEqualTo(200);
        assertThat(full.getBody()).isEqualTo(bytes);
        var first = controller.original("doc", "STUDENT", "student", "bytes=0-9");
        assertThat(first.getStatusCode().value()).isEqualTo(206);
        assertThat(first.getBody()).hasSize(10);
        assertThat(first.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 0-9/" + bytes.length);
        var suffix = controller.original("doc", "STUDENT", "student", "bytes=-4");
        assertThat(new String(suffix.getBody(), StandardCharsets.US_ASCII)).isEqualTo("file");
        assertThat(controller.original("doc", "STUDENT", "student", "bytes=999-1000").getStatusCode().value()).isEqualTo(416);
        assertThat(controller.original("doc", "STUDENT", "student", "bytes=0-1,3-4").getStatusCode().value()).isEqualTo(416);
    }

    @Test void rawReaderRequiresAuthenticatedIdentityBeforeReadingStorage() {
        assertThatThrownBy(() -> controller.original("doc", "STUDENT", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.library("doc", null, "student")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(catalog);
    }

    @Test void workspaceFailuresUseApiResponseAndKeepNoteConflictMessage() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new AiApiExceptionHandler()).build();
        when(workspace.items("student", "STUDENT", null, null)).thenThrow(new IllegalArgumentException("invalid personal item"));
        mvc.perform(get("/api/ai/knowledge/me/items").header("X-User-Id", "student").header("X-User-Role", "STUDENT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.message").value("invalid personal item"));
        doThrow(new IllegalStateException("学习记录已被修改，请刷新后重试")).when(workspace).saveItem(any(), eq("student"), eq("STUDENT"));
        mvc.perform(post("/api/ai/knowledge/me/items").header("X-User-Id", "student").header("X-User-Role", "STUDENT")
                        .contentType("application/json").content("{\"topicId\":\"topic\",\"kind\":\"NOTE\",\"note\":\"unsaved\",\"expectedRevision\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.message").value("学习记录已被修改，请刷新后重试"));
        mvc.perform(post("/api/ai/knowledge/me/items").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1));
    }
}
