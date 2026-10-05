package com.aicampus.resume.workspace;

import com.aicampus.resume.controller.ResumeWorkspaceController;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeWorkspaceControllerTest {
    @Test void allWorkspaceReadsRequireStudentIdentity() {
        ResumeWorkspaceController controller = new ResumeWorkspaceController(mock(WorkspaceService.class));
        WorkspaceException missing = assertThrows(WorkspaceException.class, () -> controller.profile(null, null));
        assertEquals(401, missing.status().value());
        WorkspaceException wrongRole = assertThrows(WorkspaceException.class, () -> controller.profile("student-1", "ADMIN"));
        assertEquals(401, wrongRole.status().value());
        assertThrows(WorkspaceException.class, () -> controller.interviewCandidate(null,null,
                new WorkspaceService.InterviewCandidateRequest("IS-1","Q1","IA-1")));
        assertThrows(WorkspaceException.class, () -> controller.interviewCandidate("u1","COMPANY",
                new WorkspaceService.InterviewCandidateRequest("IS-1","Q1","IA-1")));
    }

    @Test void exportFileRequiresStudentIdentityAndReturnsJsonErrors() throws Exception {
        WorkspaceService service = mock(WorkspaceService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ResumeWorkspaceController(service)).build();
        mvc.perform(get("/api/resumes/exports/export1/files/pdf"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("code").value(1));
        mvc.perform(get("/api/resumes/exports/export1/files/pdf").header("X-User-Id", "u1").header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value(1));
        verifyNoInteractions(service);
    }

    @Test void exportFileReturnsOwnedSnapshotBytesWithUnicodeFilename() throws Exception {
        WorkspaceService service = mock(WorkspaceService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ResumeWorkspaceController(service)).build();
        byte[] pdf = "%PDF-1.7 snapshot".getBytes(StandardCharsets.UTF_8);
        byte[] word = new byte[] {80, 75, 3, 4};
        when(service.exportContent("u1", "export1", "pdf"))
                .thenReturn(new WorkspaceService.ExportContent(pdf, "application/pdf", "\u5f20\u540c\u5b66.pdf", 3));
        when(service.exportContent("u1", "export1", "docx"))
                .thenReturn(new WorkspaceService.ExportContent(word, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "\u5f20\u540c\u5b66.docx", 3));
        var pdfResult = mvc.perform(get("/api/resumes/exports/export1/files/pdf").header("X-User-Id", "u1").header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(pdf)).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        ContentDisposition disposition = ContentDisposition.parse(pdfResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("inline", disposition.getType());
        assertEquals("\u5f20\u540c\u5b66.pdf", disposition.getFilename());
        assertTrue(pdfResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION).contains("filename*=UTF-8''"));
        var wordResult = mvc.perform(get("/api/resumes/exports/export1/files/docx").header("X-User-Id", "u1").header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(content().bytes(word)).andReturn();
        assertEquals("attachment", ContentDisposition.parse(wordResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).getType());
    }

    @Test void invalidOrInaccessibleExportFileRemainsStandardJson() throws Exception {
        WorkspaceService service = mock(WorkspaceService.class);
        when(service.exportContent(anyString(), anyString(), anyString())).thenThrow(WorkspaceException.notFound());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ResumeWorkspaceController(service)).build();
        for (String format : java.util.List.of("txt", "pdf", "docx")) {
            mvc.perform(get("/api/resumes/exports/missing/files/" + format).header("X-User-Id", "u1").header("X-User-Role", "STUDENT"))
                    .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("code").value(1));
        }
    }
}
