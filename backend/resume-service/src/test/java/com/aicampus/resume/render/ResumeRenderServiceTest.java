package com.aicampus.resume.render;

import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
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
                assertTrue(zip.getEntry("word/header1.xml") != null, "source decoration header missing: " + id);
                String header = new String(zip.getInputStream(zip.getEntry("word/header1.xml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(header.contains("Source-derived decoration"), id);
                assertFalse(documentXml.contains("TECHNICAL PROFILE") || documentXml.contains("PROJECT FOCUS"), id);
            }
            try (PDDocument pdf = PDDocument.load(result.pdf().toFile())) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains("张同学"), id);
                assertTrue(text.contains("Java"), id);
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
        DraftData data = new DraftData(List.of(new DraftBlock("skills", "SKILLS", "技能", List.of(bullets, hidden, unconfirmed), true)), List.of(), List.of(), List.of(), "FACT_ONLY");
        ResumeDraft draft = new ResumeDraft("filtered", "resume-filtered", "user-1", 1, 1, sample("T01").profileSnapshot(), "T01", "1.0.0", "Java", null, "filtered", data, true, false, Instant.now(), Instant.now());
        ResumeRenderService.RenderedResume result = new ResumeRenderService(new ResumeTemplateRegistry(new ObjectMapper()), soffice, 60, 1).render(draft, temp);
        try (PDDocument pdf = PDDocument.load(result.pdf().toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Java"));
            assertTrue(text.contains("Redis"));
            assertFalse(text.contains("SECRET_HIDDEN"));
            assertFalse(text.contains("SECRET_UNCONFIRMED"));
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
        List<Experience> many = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new Experience("long-" + i, "PROJECT", "项目 " + i, "课程小组", "2024", "2025", "成员", "完成用户需求分析与实现", "使用 Java 与 Spring Boot 进行开发和测试", "完成接口联调并记录结果", List.of("Java"), List.of(), src, true)).toList();
        ProfileData longProfile = new ProfileData(base.profileSnapshot().basics(), base.profileSnapshot().education(), base.profileSnapshot().skills(), many, List.of(), base.profileSnapshot().availability());
        ResumeDraft longSingle = new ResumeDraft("long-single", "r", "u", 1, 1, longProfile, "T01", "1.0.0", "Java", null, "long-single", new DraftData(List.of(), List.of(), List.of(), List.of(), "FACT_ONLY"), true, false, Instant.now(), Instant.now());
        ResumeRenderService.RenderedResume singleResult = service.render(longSingle, temp.resolve("long-single"));
        assertTrue(singleResult.layoutIssues().stream().anyMatch(x -> x.startsWith("NEEDS_EDIT")));
        ResumeDraft longTwo = new ResumeDraft("long-two", "r", "u", 1, 1, longProfile, "T07", "1.0.0", "Java", null, "long-two", longSingle.data(), true, false, Instant.now(), Instant.now());
        ResumeRenderService.RenderedResume twoResult = service.render(longTwo, temp.resolve("long-two"));
        assertTrue(twoResult.pageCount() <= 2, "two-page template overflowed: " + twoResult.layoutIssues());
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
