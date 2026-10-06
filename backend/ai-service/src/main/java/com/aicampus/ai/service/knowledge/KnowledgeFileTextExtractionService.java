package com.aicampus.ai.service.knowledge;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
            ExtractedKnowledgeText extracted = switch (extension) {
                case "txt", "md" -> new ExtractedKnowledgeText(extension, normalize(extractPlainText(bytes)), List.of());
                case "pdf" -> extractPdf(new ByteArrayInputStream(bytes));
                case "docx" -> new ExtractedKnowledgeText(extension, normalize(extractDocx(new ByteArrayInputStream(bytes))), List.of());
                case "doc" -> new ExtractedKnowledgeText(extension, normalize(extractDoc(new ByteArrayInputStream(bytes))), List.of());
                default -> throw new IllegalArgumentException("Unsupported knowledge file format: " + extension);
            };
            if (extracted.text().isBlank()) {
                throw new IllegalArgumentException("No readable text was extracted; scanned documents require a text-based original (OCR is unavailable)");
            }
            int maxLength = Math.max(1_000, properties.getIngestion().getMaxTextChars());
            if (extracted.text().length() > maxLength) {
                throw new IllegalArgumentException("Knowledge file contains " + extracted.text().length()
                        + " text characters, exceeding the limit of " + maxLength
                        + "; split the document before importing. No text has been silently discarded");
            }
            return extracted;
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

    private ExtractedKnowledgeText extractPdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDF files are not supported");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            StringBuilder fullText = new StringBuilder();
            List<PageSpan> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (fullText.length() > 0 && !text.isEmpty()) fullText.append('\n');
                int startOffset = fullText.length();
                fullText.append(text);
                pages.add(new PageSpan(page, startOffset, fullText.length()));
            }
            return new ExtractedKnowledgeText("pdf", fullText.toString(), List.copyOf(pages));
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

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll(" {2,}", " ")
                .trim();
        return normalized;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    public record PageSpan(int pageNumber, int startOffset, int endOffset) {
    }

    public record ExtractedKnowledgeText(String fileFormat, String text, List<PageSpan> pages) {
        public ExtractedKnowledgeText(String fileFormat, String text) {
            this(fileFormat, text, List.of());
        }
    }
}
