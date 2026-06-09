package com.aicampus.resume.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicampus.resume.ResumeServiceApplication;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ResumeServiceApplication.class, properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
class ResumeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadReturnsResumeSummary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "demo".getBytes());
        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data.studentId").value("S001"))
                .andExpect(jsonPath("$.data.storageProvider").value("local-demo"))
                .andExpect(jsonPath("$.data.storageStatus").value("SKIPPED"))
                .andExpect(jsonPath("$.data.sourceFormat").value("PDF"))
                .andExpect(jsonPath("$.data.parseStatus").value("UNPARSED"))
                .andExpect(jsonPath("$.data.parsedTextLength").value(0));
    }

    @Test
    void uploadUsesStudentHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "demo".getBytes());
        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header("X-User-Id", "S-GATEWAY-001")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("S-GATEWAY-001"))
                .andExpect(jsonPath("$.data.fileName").value("resume.pdf"));
    }

    @Test
    void uploadDocxExtractsTextForDiagnosis() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("软件工程本科，熟悉 Java、Spring Boot、MySQL、Redis，并做过校园招聘平台。"));

        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("resume.docx"))
                .andExpect(jsonPath("$.data.education").value("已读取简历正文"))
                .andExpect(jsonPath("$.data.diagnosis").value("已读取简历正文，点击诊断生成 AI 建议。"))
                .andExpect(jsonPath("$.data.skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.sourceFormat").value("DOCX"))
                .andExpect(jsonPath("$.data.parseStatus").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.storageStatus").value("SKIPPED"));
    }

    @Test
    void analyzeFallsBackWhenAiServiceIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/resumes/R001/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(86))
                .andExpect(jsonPath("$.data.objectKey").value("resumes/R001/demo-resume.pdf"))
                .andExpect(jsonPath("$.data.skills[4]").value("Docker"));
    }

    private static byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
