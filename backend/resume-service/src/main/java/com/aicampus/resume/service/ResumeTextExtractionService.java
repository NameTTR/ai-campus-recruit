package com.aicampus.resume.service;

import java.io.InputStream;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeTextExtractionService {
    private static final int MAX_TEXT_LENGTH = 12_000;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }

        String extension = extension(file.getOriginalFilename());
        if (!"pdf".equals(extension) && !"docx".equals(extension) && !"doc".equals(extension)) {
            return "";
        }

        try (InputStream inputStream = file.getInputStream()) {
            String text = switch (extension) {
                case "pdf" -> extractPdf(inputStream);
                case "docx" -> extractDocx(inputStream);
                case "doc" -> extractDoc(inputStream);
                default -> "";
            };
            return normalize(text);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractPdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            if (document.isEncrypted()) {
                return "";
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

    private static String extension(String fileName) {
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
        return normalized.length() <= MAX_TEXT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TEXT_LENGTH);
    }
}
