package com.aicampus.ai.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class KnowledgeFileTextExtractionServiceTest {
    private final KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
    private final KnowledgeFileTextExtractionService service = new KnowledgeFileTextExtractionService(properties);

    @Test void pdfPageOffsetsAddressTheSameFullTextUsedForCitations() throws Exception {
        var result = service.extract(pdf("First page sample.", "Second page sample."), "two.pdf");
        assertThat(result.pages()).hasSize(2);
        for (var page : result.pages()) {
            assertThat(result.text().substring(page.startOffset(), page.endOffset()))
                    .isEqualTo(page.pageNumber() == 1 ? "First page sample." : "Second page sample.");
        }
    }

    @Test void textAboveLimitFailsInsteadOfSilentlyDiscardingTheLastPart() {
        properties.getIngestion().setMaxTextChars(1000);
        assertThatThrownBy(() -> service.extract("a".repeat(1001).getBytes(StandardCharsets.UTF_8), "long.txt"))
                .hasMessageContaining("exceeding the limit").hasMessageContaining("No text has been silently discarded");
    }

    @Test void chineseAndParagraphsRemainReadableInPlainText() {
        var result = service.extract("# 中文资料\r\n\r\n数据口径要保留来源。\n下一段。".getBytes(StandardCharsets.UTF_8), "note.md");
        assertThat(result.text()).contains("中文资料", "数据口径要保留来源。", "下一段。");
        assertThat(result.pages()).isEmpty();
    }

    @Test void brokenPdfReportsFailureAndEmptyPdfReportsOcrBoundary() throws Exception {
        assertThatThrownBy(() -> service.extract("not a PDF".getBytes(StandardCharsets.UTF_8), "broken.pdf"))
                .hasMessageContaining("Failed to extract");
        assertThatThrownBy(() -> service.extract(pdf(), "scan.pdf"))
                .hasMessageContaining("No readable text").hasMessageContaining("OCR");
    }

    @Test void encryptedPdfCannotBeImportedAsStudentReadableText() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner", "", new AccessPermission()));
            document.save(out);
            bytes = out.toByteArray();
        }
        assertThatThrownBy(() -> service.extract(bytes, "encrypted.pdf")).hasMessageContaining("Encrypted PDF");
    }

    @Test void wordPreservesChineseTableTextWithoutInventingPageNumbers() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("运营指标的统计口径");
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("指标");
            table.getRow(0).getCell(1).setText("验证方式");
            table.getRow(1).getCell(0).setText("报名转化率");
            table.getRow(1).getCell(1).setText("核对去重人数与统计时间");
            document.write(out);
            bytes = out.toByteArray();
        }
        var result = service.extract(bytes, "metrics.docx");
        assertThat(result.text()).contains("运营指标", "报名转化率", "核对去重人数与统计时间");
        assertThat(result.pages()).isEmpty();
    }

    private byte[] pdf(String... pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (pages.length == 0) document.addPage(new PDPage());
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
