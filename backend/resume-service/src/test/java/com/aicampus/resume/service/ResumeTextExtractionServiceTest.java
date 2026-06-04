package com.aicampus.resume.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ResumeTextExtractionServiceTest {
    private final ResumeTextExtractionService service = new ResumeTextExtractionService();

    @Test
    void extractsTextFromDocxResume() throws Exception {
        byte[] bytes = docxBytes("候选人具备 Java、Spring Boot、MySQL 和 Redis 项目经验。");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes);

        String text = service.extract(file);

        assertThat(text).contains("Java", "Spring Boot", "MySQL", "Redis");
    }

    @Test
    void extractsTextFromPdfResume() throws Exception {
        byte[] bytes = pdfBytes("Candidate has Java Spring Boot MySQL Redis project experience.");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes);

        String text = service.extract(file);

        assertThat(text).contains("Java", "Spring Boot", "MySQL", "Redis");
    }

    private static byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
