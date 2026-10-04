package com.aicampus.resume.render;

import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.util.Base64;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ResumeRenderServiceTest {
    @TempDir Path temp;

    @Test
    void registryContainsEightAdaptedTemplates() {
        ResumeTemplateRegistry registry = new ResumeTemplateRegistry(new ObjectMapper());
        assertEquals(8, registry.list().size());
        assertEquals("T04", registry.get("T04").id());
        assertTrue(registry.list().stream().allMatch(t -> t.previewUrl().startsWith("/resume-templates/")));
    }

    @Test
    void rendersShortResumeAsEditableDocxAndSearchablePdf() throws Exception {
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)), "LibreOffice is not installed in this test environment");
        ResumeRenderService service = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1);
        ResumeRenderService.RenderedResume result = service.render(sample("T03"), temp);
        assertTrue(Files.size(result.docx()) > 1000);
        assertTrue(Files.size(result.pdf()) > 1000);
        assertTrue(result.pageCount() >= 1);
        try (PDDocument pdf = PDDocument.load(result.pdf().toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("张同学"));
            assertTrue(text.contains("校园项目"));
            assertFalse(text.contains("作品已经注册版权"));
            assertFalse(text.contains("ibaotu.com"));
        }
    }

    @Test
    void allEightTemplatesRenderAndPreserveProvidedFacts() throws Exception {
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)), "LibreOffice is not installed in this test environment");
        ResumeRenderService service = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1);
        for (String id : List.of("T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08")) {
            ResumeRenderService.RenderedResume result = service.render(sample(id), temp.resolve(id));
            assertTrue(Files.exists(result.docx()), id);
            assertTrue(Files.exists(result.pdf()), id);
            assertEquals(1, result.pageCount(), "short content must stay on one page: " + id);
            try (ZipFile zip = new ZipFile(result.docx().toFile())) {
                String documentXml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(documentXml.contains("photo"), "no-photo export embedded a photograph: " + id);
                java.util.regex.Matcher sizes = java.util.regex.Pattern.compile("<w:sz w:val=\"(\\d+)\"").matcher(documentXml);
                while (sizes.find()) assertTrue(Integer.parseInt(sizes.group(1)) >= 20, "text smaller than 10pt: " + id);
                assertTrue(zip.getEntry("word/header1.xml") != null, "editable header missing: " + id);
                String header = new String(zip.getInputStream(zip.getEntry("word/header1.xml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(header.contains("wp:anchor"), "page-sized floating artwork can cover contact information: " + id);
                assertFalse(documentXml.contains("TECHNICAL PROFILE") || documentXml.contains("PROJECT FOCUS"), id);
            }
            try (PDDocument pdf = PDDocument.load(result.pdf().toFile())) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains("张同学"), id);
                assertTrue(text.contains("Java"), id);
                assertTextStaysInsidePageAndDoesNotOverlap(pdf, id);
            }
            try (var word = new org.apache.poi.xwpf.usermodel.XWPFDocument(Files.newInputStream(result.docx()))) {
                var section = word.getDocument().getBody().getSectPr();
                int available = Integer.parseInt(section.getPgSz().getW().toString()) - Integer.parseInt(section.getPgMar().getLeft().toString()) - Integer.parseInt(section.getPgMar().getRight().toString());
                for (var table : word.getTables()) {
                    assertEquals(available, table.getWidth(), "table must fit the editable page width: " + id);
                    var grid = table.getCTTbl().getTblGrid();
                    assertEquals(available, grid.getGridColList().stream().mapToInt(c -> Integer.parseInt(c.getW().toString())).sum(), "Word column grid disagrees with page geometry: " + id);
                }
                if (id.equals("T07")) {
                    assertTrue(word.getParagraphs().stream().flatMap(p -> p.getRuns().stream()).anyMatch(r -> r instanceof org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun && "40546F".equals(r.getColor())), "pastel artwork must not make portfolio links unreadable");
                }
            }
        }
    }

    @Test
    void doesNotRenderHiddenOrUnconfirmedDraftEntriesAndKeepsEmptyTitleBullets() throws Exception {
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)));
        SourceRef confirmed = new SourceRef("USER", "confirmed", "confirmed", true, "USER_CONFIRMED");
        DraftEntry bullets = new DraftEntry("skills-1", "", "", List.of("Java", "Redis"), List.of(), List.of("skill-1"), true, true);
        DraftEntry hidden = new DraftEntry("hidden", "不应导出", "", List.of("SECRET_HIDDEN"), List.of(), List.of(), false, true);
        DraftEntry unconfirmed = new DraftEntry("unconfirmed", "不应导出", "", List.of("SECRET_UNCONFIRMED"), List.of(), List.of(), true, false);
        DraftData data = new DraftData(List.of(new DraftBlock("skills", "SKILLS", "技能", List.of(bullets, hidden, unconfirmed), true), new DraftBlock("empty", "PROJECT", "EMPTY_SECTION", List.of(hidden, unconfirmed), true)), List.of(), List.of(), List.of(), "FACT_ONLY");
        ResumeDraft draft = new ResumeDraft("filtered", "resume-filtered", "user-1", 1, 1, sample("T01").profileSnapshot(), "T01", "1.0.0", "Java", null, "filtered", data, true, false, Instant.now(), Instant.now());
        ResumeRenderService.RenderedResume result = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1).render(draft, temp);
        try (PDDocument pdf = PDDocument.load(result.pdf().toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Java"));
            assertTrue(text.contains("Redis"));
            assertFalse(text.contains("SECRET_HIDDEN"));
            assertFalse(text.contains("SECRET_UNCONFIRMED"));
            assertFalse(text.contains("EMPTY_SECTION"), "sections with no visible confirmed entries must not leave orphan headings");
            assertFalse(text.contains("不应导出"));
        }
    }

    @Test
    void rejectsNonHttpLinksAndEmbedsOptionalPhoto() throws Exception {
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)));
        ResumeRenderService service = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1);
        ResumeDraft invalid = sample("T04");
        ProfileData p = invalid.profileSnapshot();
        Experience e = p.experiences().get(0);
        ProfileData withBadLink = new ProfileData(p.basics(), p.education(), p.skills(), List.of(new Experience(e.id(), e.type(), e.title(), e.organization(), e.startDate(), e.endDate(), e.role(), e.actions(), e.methods(), e.results(), e.skills(), List.of("javascript:alert(1)"), e.source(), e.confirmed())), p.credentials(), p.availability());
        ResumeDraft bad = new ResumeDraft(invalid.id(), invalid.resumeId(), invalid.userId(), invalid.revision(), invalid.profileRevision(), withBadLink, invalid.templateId(), invalid.templateVersion(), invalid.targetRole(), invalid.jobSnapshot(), invalid.inputFingerprint(), invalid.data(), invalid.confirmed(), invalid.sourceStale(), invalid.createdAt(), invalid.updatedAt());
        assertThrows(IllegalArgumentException.class, () -> service.render(bad, temp));
        byte[] photo = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        ResumeRenderService.RenderedResume result = service.render(sample("T04"), temp, photo);
        try (ZipFile zip = new ZipFile(result.docx().toFile())) {
            assertTrue(zip.stream().anyMatch(e2 -> e2.getName().startsWith("word/media/")));
        }
    }

    @Test
    void allEightTemplatesUseTheUploadedPhotoAndRetainEditableText() throws Exception {
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)));
        ResumeRenderService service = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1);
        byte[] photo = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        for (String id : List.of("T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08")) {
            ResumeRenderService.RenderedResume rendered = service.render(sample(id), temp.resolve("photo-" + id), photo);
            assertEquals(1, rendered.pageCount(), id);
            try (org.apache.poi.xwpf.usermodel.XWPFDocument word = new org.apache.poi.xwpf.usermodel.XWPFDocument(Files.newInputStream(rendered.docx()))) {
                assertTrue(word.getAllPictures().stream().anyMatch(picture -> java.util.Arrays.equals(photo, picture.getData())), "uploaded photo absent: " + id);
            }
            try (PDDocument pdf = PDDocument.load(rendered.pdf().toFile())) {
                assertTrue(new PDFTextStripper().getText(pdf).contains("张同学"), id);
                assertTextStaysInsidePageAndDoesNotOverlap(pdf, id + "-photo");
            }
        }
    }

    @Test
    void adaptedBasesContainOnlySourceDecorationsAndNoSamplePersonalData() throws Exception {
        ResumeTemplateRegistry registry = new ResumeTemplateRegistry(new ObjectMapper());
        try (var in = new org.springframework.core.io.ClassPathResource("resume-templates/decoration-provenance.json").getInputStream()) {
            var provenance = new ObjectMapper().readTree(in);
            assertEquals(8, provenance.size());
            for (var entry : provenance) {
                assertEquals("source-pdf-vector-paths", entry.path("method").asText());
                assertFalse(entry.path("sourceSha256").asText().isBlank());
                assertTrue(entry.path("backgroundPathCount").asInt() > 0);
            }
        }
        for (var entry : registry.list()) {
            try (var in = new org.springframework.core.io.ClassPathResource("resume-templates/base/" + entry.id() + ".docx").getInputStream();
                 var word = new org.apache.poi.xwpf.usermodel.XWPFDocument(in)) {
                assertTrue(word.getParagraphs().stream().allMatch(p -> p.getText().isBlank()), entry.id());
                assertTrue(word.getAllPictures().isEmpty(), "base body carries sample photograph: " + entry.id());
                assertEquals("AI Campus Recruit", word.getProperties().getCoreProperties().getCreator());
                assertTrue(word.getHeaderList().stream().flatMap(h -> h.getAllPictures().stream()).count() == 1, entry.id());
            }
        }
    }

    @Test
    void longSinglePageResumeReportsNeedsEditAndLongTwoPageResumeStaysWithinBudget() throws Exception {
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)));
        ResumeRenderService service = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1);
        ResumeDraft base = sample("T01");
        SourceRef src = new SourceRef("USER", "long", "long", true, "USER_CONFIRMED");
        List<Experience> many = java.util.stream.IntStream.range(0, 12).mapToObj(i -> new Experience("long-" + i, "PROJECT", "项目 " + i, "课程小组", "2024", "2025", "成员", "完成用户需求分析与实现", "使用 Java 与 Spring Boot 进行开发和测试", "完成接口联调并记录结果", List.of("Java"), List.of(), src, true)).toList();
        ProfileData longProfile = new ProfileData(base.profileSnapshot().basics(), base.profileSnapshot().education(), base.profileSnapshot().skills(), many, List.of(), base.profileSnapshot().availability());
        ResumeDraft longSingle = new ResumeDraft("long-single", "r", "u", 1, 1, longProfile, "T01", "1.0.0", "Java", null, "long-single", new DraftData(List.of(), List.of(), List.of(), List.of(), "FACT_ONLY"), true, false, Instant.now(), Instant.now());
        ResumeRenderService.RenderedResume singleResult = service.render(longSingle, temp.resolve("long-single"));
        assertTrue(singleResult.layoutIssues().stream().anyMatch(x -> x.startsWith("NEEDS_EDIT")));
        ResumeDraft longTwo = new ResumeDraft("long-two", "r", "u", 1, 1, longProfile, "T07", "1.0.0", "Java", null, "long-two", longSingle.data(), true, false, Instant.now(), Instant.now());
        ResumeRenderService.RenderedResume twoResult = service.render(longTwo, temp.resolve("long-two"));
        assertTrue(twoResult.pageCount() <= 2, "two-page template overflowed: " + twoResult.layoutIssues());
        assertEquals(2, twoResult.pageCount(), "long content should flow to a second page rather than squeeze into columns");
        try (PDDocument pdf = PDDocument.load(twoResult.pdf().toFile())) {
            assertTextStaysInsidePageAndDoesNotOverlap(pdf, "T07-long");
            assertTrue(new PDFTextStripper().getText(pdf).contains("项目 11"), "later entries must survive natural pagination");
        }
    }

    @Test
    void navyHeaderKeepsLongContactInformationVisibleAndUsesFlowingBackground() throws Exception {
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)));
        ResumeDraft base = sample("T05");
        ProfileData p = base.profileSnapshot();
        ProfileData profile = new ProfileData(new BasicInfo("张同学", "13800000000", "student.with.a.long.email@example.com", "浙江省杭州市", "https://example.com/student/portfolio/details", null), p.education(), p.skills(), p.experiences(), p.credentials(), p.availability());
        ResumeDraft draft = new ResumeDraft(base.id(), base.resumeId(), base.userId(), base.revision(), base.profileRevision(), profile, base.templateId(), base.templateVersion(), base.targetRole(), base.jobSnapshot(), base.inputFingerprint(), base.data(), base.confirmed(), base.sourceStale(), base.createdAt(), base.updatedAt());
        var result = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1).render(draft, temp);
        try (var pdf = PDDocument.load(result.pdf().toFile())) {
            assertTextStaysInsidePageAndDoesNotOverlap(pdf, "T05-long-contact");
            var image = new PDFRenderer(pdf).renderImageWithDPI(0, 144);
            PDFTextStripper contacts = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition text) {
                    try {
                        if (!text.getUnicode().isBlank() && getGraphicsState().getNonStrokingColor().toRGB() == 0xFFFFFF) {
                            int x = Math.max(0, Math.round((text.getXDirAdj() - 2) * 2));
                            int y = Math.max(0, Math.round((text.getYDirAdj() - text.getHeightDir() / 2) * 2));
                            int rgb = image.getRGB(x, y) & 0xFFFFFF;
                            assertNotEquals(0xFFFFFF, rgb, "white header text escaped its navy background: " + text.getUnicode());
                        }
                    } catch (java.io.IOException error) { throw new IllegalStateException(error); }
                    super.processTextPosition(text);
                }
            };
            String text = contacts.getText(pdf);
            assertTrue(text.contains("student.with.a.long.email@example.com"));
            assertTrue(text.contains("https://example.com/student/portfolio/details"));
        }
    }

    private static void assertTextStaysInsidePageAndDoesNotOverlap(PDDocument pdf, String label) throws Exception {
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            List<TextPosition> positions = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition text) {
                    if (!text.getUnicode().isBlank()) positions.add(text);
                    super.processTextPosition(text);
                }
            };
            stripper.setStartPage(page); stripper.setEndPage(page); stripper.getText(pdf);
            float width = pdf.getPage(page - 1).getMediaBox().getWidth();
            float height = pdf.getPage(page - 1).getMediaBox().getHeight();
            for (TextPosition text : positions) {
                assertTrue(text.getXDirAdj() >= 38 && text.getXDirAdj() + text.getWidthDirAdj() <= width - 38, label + " text crossed horizontal page margins: " + text.getUnicode());
                assertTrue(text.getYDirAdj() - text.getHeightDir() >= 30 && text.getYDirAdj() <= height - 35, label + " text crossed vertical page margins: " + text.getUnicode());
            }
            for (int i = 0; i < positions.size(); i++) for (int j = i + 1; j < positions.size(); j++) {
                TextPosition a = positions.get(i), b = positions.get(j);
                if (Math.abs(a.getYDirAdj() - b.getYDirAdj()) < 2) continue;
                float xOverlap = Math.min(a.getXDirAdj() + a.getWidthDirAdj(), b.getXDirAdj() + b.getWidthDirAdj()) - Math.max(a.getXDirAdj(), b.getXDirAdj());
                float yOverlap = Math.min(a.getYDirAdj(), b.getYDirAdj()) - Math.max(a.getYDirAdj() - a.getHeightDir(), b.getYDirAdj() - b.getHeightDir());
                assertFalse(xOverlap > 1 && yOverlap > 1, label + " text lines overlap: " + a.getUnicode() + " / " + b.getUnicode());
            }
        }
    }

    @Test
    void generatesSyntheticPreviewsFromActualRendererWhenRequested() throws Exception {
        String requested = System.getProperty("resume.preview.output");
        Assumptions.assumeTrue(requested != null && !requested.isBlank());
        String soffice = System.getenv().getOrDefault("RESUME_SOFFICE_PATH", "C:/Program Files/LibreOffice/program/soffice.com");
        Assumptions.assumeTrue(Files.exists(Path.of(soffice)));
        Path output = Path.of(requested); Files.createDirectories(output);
        ResumeRenderService service = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1);
        for (String id : List.of("T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08")) {
            ResumeRenderService.RenderedResume rendered = service.render(sample(id), output.resolve("render-" + id));
            try (PDDocument pdf = PDDocument.load(rendered.pdf().toFile())) {
                var image = new PDFRenderer(pdf).renderImageWithDPI(0, 110);
                ImageIO.write(image, "png", output.resolve(id + ".png").toFile());
            }
        }
    }

    private static ResumeDraft sample(String template) {
        SourceRef source = new SourceRef("USER", "fact-1", "校园项目", true, "USER_CONFIRMED");
        ProfileData profile = new ProfileData(
                new BasicInfo("张同学", "13800000000", "student@example.com", "杭州", "https://example.com", null),
                List.of(new Education("edu-1", "某大学", "计算机科学与技术", "本科", "2022-09", "2026-06", "2026-06", List.of("数据结构"), "", source)),
                List.of(new SkillItem("skill-java", "Java", source), new SkillItem("skill-spring", "Spring Boot", source)),
                List.of(new Experience("exp-1", "PROJECT", "校园项目", "课程小组", "2025-03", "2025-06", "开发成员", "实现用户登录和任务管理", "使用 Spring Boot 与 MySQL", "完成接口联调并通过测试", List.of("Java"), List.of("https://example.com/project"), source, true)),
                List.of(), new Availability(List.of("杭州"), "2026-07-01", 5, 3, "2026-06"));
        DraftData data = new DraftData(List.of(), List.of(), List.of(), List.of(), "FACT_ONLY");
        Instant now = Instant.now();
        return new ResumeDraft("draft-1", "resume-1", "user-1", 1, 1, profile, template, "1.0.0", "Java", null, "fingerprint", data, true, false, now, now);
    }
}
