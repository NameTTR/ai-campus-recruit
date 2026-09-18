package com.aicampus.resume.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.resume.ResumeServiceApplication;
import com.aicampus.resume.client.AiAnalyzeClient;
import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.dto.AiAnalyzeResponse;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = ResumeServiceApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "demo.seed.enabled=false"
})
@AutoConfigureMockMvc
class ResumeControllerTest {
    private static final String STUDENT_ID = "S-RESUME-TEST";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiAnalyzeClient aiAnalyzeClient;

    @Test
    void realAiDiagnosisIsRetainedWhenModelOmitsMachineReadableScore() throws Exception {
        org.mockito.Mockito.when(aiAnalyzeClient.analyze(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ApiResponse.ok(new AiAnalyzeResponse("resume", "dashscope", "## 诊断\n项目成果明确，建议补充测试证据。", false)));
        String id = resumeId(upload("Bachelor degree\nSkills: Java\nProject: Campus platform", STUDENT_ID));
        mockMvc.perform(post("/api/resumes/{id}/analyze", id)
                        .headers(studentHeaders(STUDENT_ID)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetJob\":\"Java 实习生\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosis").value(containsString("项目成果明确")));
        mockMvc.perform(get("/api/resumes/{id}/diagnoses", id).headers(studentHeaders(STUDENT_ID)))
                .andExpect(jsonPath("$.data[0].source").value("AI_TEXT_RULE_SCORE"));
    }

    @Test
    void upstreamDemoResponseRemainsExplicitlyRuleBased() throws Exception {
        org.mockito.Mockito.when(aiAnalyzeClient.analyze(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ApiResponse.ok(new AiAnalyzeResponse("resume", "local-demo", "Demo score: 95", true)));
        String id = resumeId(upload("Bachelor degree\nSkills: Java\nProject: Campus platform", STUDENT_ID));
        mockMvc.perform(post("/api/resumes/{id}/analyze", id)
                        .headers(studentHeaders(STUDENT_ID)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetJob\":\"Java 实习生\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosis").value(containsString("基于规则的诊断")));
        mockMvc.perform(get("/api/resumes/{id}/diagnoses", id).headers(studentHeaders(STUDENT_ID)))
                .andExpect(jsonPath("$.data[0].source").value("RULE_FALLBACK"));
    }

    @Test
    void uploadExtractsOnlyEvidencePresentInTheDocument() throws Exception {
        MvcResult result = upload("Bachelor of Software Engineering\nSkills: Java, Spring Boot, MySQL\n"
                + "Project: Built a recruitment API with measured latency.", STUDENT_ID);

        mockMvc.perform(get("/api/resumes/{id}", resumeId(result)).headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.education").value(containsString("Bachelor")))
                .andExpect(jsonPath("$.data.skills", hasItem("Java")))
                .andExpect(jsonPath("$.data.projects[0]").value(containsString("Project")))
                .andExpect(jsonPath("$.data.score").value(lessThan(70)))
                .andExpect(jsonPath("$.data.parseStatus").value("TEXT_EXTRACTED"));
    }

    @Test
    void frontendSkillsDoNotInventJavaOrInferJavaScriptFromTypeScript() throws Exception {
        MvcResult result = upload("Bachelor degree\nSkills: JavaScript, TypeScript, Vue\nProject: Campus website", STUDENT_ID);
        mockMvc.perform(get("/api/resumes/{id}", resumeId(result)).headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills", hasItem("JavaScript")))
                .andExpect(jsonPath("$.data.skills", hasItem("TypeScript")))
                .andExpect(jsonPath("$.data.skills", org.hamcrest.Matchers.not(hasItem("Java"))));
        MvcResult typed = upload("Bachelor degree\nSkills: TypeScript\nProject: Campus website", STUDENT_ID);
        mockMvc.perform(get("/api/resumes/{id}", resumeId(typed)).headers(studentHeaders(STUDENT_ID)))
                .andExpect(jsonPath("$.data.skills", org.hamcrest.Matchers.not(hasItem("JavaScript"))));
    }

    @Test
    void correctedProfileAndDiagnosisHistoryKeepImmutableInputSnapshot() throws Exception {
        MvcResult upload = upload("Bachelor degree\nSkills: Java, Redis\nProject: Campus platform", STUDENT_ID);
        String resumeId = resumeId(upload);

        mockMvc.perform(post("/api/resumes/{id}/analyze", resumeId)
                        .headers(studentHeaders(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\",\"targetJob\":\"Backend Engineer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosis").value(containsString("基于规则的诊断")))
                .andExpect(jsonPath("$.data.score").value(lessThan(86)));

        mockMvc.perform(patch("/api/resumes/{id}/profile", resumeId)
                        .headers(studentHeaders(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"Python\"],\"projects\":[\"Project: Updated profile\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills[0]").value("Python"));

        mockMvc.perform(get("/api/resumes/{id}/diagnoses", resumeId).headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].targetJob").value("Backend Engineer"))
                .andExpect(jsonPath("$.data[0].source").value("RULE_FALLBACK"))
                .andExpect(jsonPath("$.data[0].skillsSnapshot", hasItem("Java")))
                .andExpect(jsonPath("$.data[0].resumeTextSnapshot").value(containsString("Campus platform")));
    }

    @Test
    void ownershipIsAppliedToReadWriteAndList() throws Exception {
        MvcResult upload = upload("Bachelor degree\nSkills: Java\nProject: Private service", STUDENT_ID);
        String resumeId = resumeId(upload);

        mockMvc.perform(get("/api/resumes/{id}", resumeId).headers(studentHeaders("S-OTHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(delete("/api/resumes/{id}", resumeId).headers(studentHeaders("S-OTHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/api/resumes").headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].studentId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(STUDENT_ID))));
    }

    @Test
    void invalidEmptyUnsupportedAndScannedUploadsReturnClearApiErrors() throws Exception {
        MockMultipartFile unsupported = new MockMultipartFile("file", "resume.txt", "text/plain", "profile".getBytes());
        mockMvc.perform(multipart("/api/resumes/upload").file(unsupported).headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unsupported")));

        MockMultipartFile scanned = new MockMultipartFile("file", "resume.pdf", "application/pdf", "image-only".getBytes());
        mockMvc.perform(multipart("/api/resumes/upload").file(scanned).headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No readable text")));

        mockMvc.perform(multipart("/api/resumes/upload").headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private MvcResult upload(String content, String studentId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes(content));
        return mockMvc.perform(multipart("/api/resumes/upload").file(file).headers(studentHeaders(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    private static String resumeId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.resumeId");
    }

    private static org.springframework.http.HttpHeaders studentHeaders(String studentId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", studentId);
        headers.add("X-User-Role", "STUDENT");
        return headers;
    }

    private static byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }
}
