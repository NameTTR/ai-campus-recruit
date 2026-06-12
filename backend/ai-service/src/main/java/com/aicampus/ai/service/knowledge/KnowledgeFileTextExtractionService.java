package com.aicampus.ai.service.knowledge;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeFileTextExtractionService {
    private final KnowledgeBaseProperties properties;

    public KnowledgeFileTextExtractionService(KnowledgeBaseProperties properties) {
        this.properties = properties;
    }

    public ExtractedKnowledgeText extract(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Knowledge file is empty");
        }
        String extension = extension(fileName);
        try {
            String text = switch (extension) {
                case "txt", "md" -> extractPlainText(bytes);
                case "pdf" -> extractPdf(new ByteArrayInputStream(bytes));
                case "docx" -> extractDocx(new ByteArrayInputStream(bytes));
                case "doc" -> extractDoc(new ByteArrayInputStream(bytes));
                default -> throw new IllegalArgumentException("Unsupported knowledge file format: " + extension);
            };
            String normalized = normalize(text, properties.getIngestion().getMaxTextChars());
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("No readable text was extracted from the knowledge file");
            }
            return new ExtractedKnowledgeText(extension, normalized);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to extract knowledge file text: " + safeMessage(ex), ex);
        }
    }

    private String extractPlainText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') < 0) {
            return utf8;
        }
        return new String(bytes, Charset.forName("GB18030"));
    }

    private String extractPdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDF files are not supported");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
                WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    public static String extension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String text, int maxTextChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll(" {2,}", " ")
                .trim();
        int maxLength = Math.max(1_000, maxTextChars);
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    public record ExtractedKnowledgeText(String fileFormat, String text) {
    }
}
