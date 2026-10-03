package com.aicampus.resume.render;

import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Generates the editable resume artifact and converts that exact artifact to PDF.
 * The renderer only writes facts already present in the confirmed draft/profile.
 */
@Service
public class ResumeRenderService {
    private static final String FONT = "Noto Sans CJK SC";
    private final ResumeTemplateRegistry registry;
    private final String sofficePath;
    private final Duration timeout;
    private final Semaphore permits;

    public ResumeRenderService(ResumeTemplateRegistry registry,
                               @Value("${resume.render.soffice-path:${RESUME_SOFFICE_PATH:soffice}}") String sofficePath,
                               @Value("${resume.render.timeout-seconds:${RESUME_RENDER_TIMEOUT_SECONDS:45}}") long timeoutSeconds,
                               @Value("${resume.render.concurrency:${RESUME_RENDER_CONCURRENCY:2}}") int concurrency) {
        this.registry = registry;
        this.sofficePath = sofficePath == null || sofficePath.isBlank() ? "soffice" : sofficePath;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.permits = new Semaphore(Math.max(1, concurrency));
    }

    public RenderedResume render(ResumeDraft draft, Path outputDirectory) throws IOException {
        return render(draft, outputDirectory, null);
    }

    public RenderedResume render(ResumeDraft draft, Path outputDirectory, byte[] photoBytes) throws IOException {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        ResumeTemplateRegistry.TemplateEntry template = registry.entry(draft.templateId());
        validateLinks(draft);
        Files.createDirectories(outputDirectory);
        Path work = Files.createTempDirectory(outputDirectory, "resume-render-");
        Path docx = work.resolve(safeName(draft.id()) + "-r" + draft.revision() + ".docx");
        Path pdf = work.resolve(safeName(draft.id()) + "-r" + draft.revision() + ".pdf");
        List<String> issues = new ArrayList<>();
        try {
            buildDocx(draft, template, photoBytes, docx);
            convertToPdf(docx, work, issues);
            Path converted = work.resolve(docx.getFileName().toString().replaceFirst("\\.docx$", ".pdf"));
            if (!Files.exists(converted)) throw new IOException("LibreOffice未生成PDF");
            if (!converted.equals(pdf)) Files.move(converted, pdf);
            int pages = pageCount(pdf);
            if (pages > template.maxPages()) {
                issues.add("NEEDS_EDIT: 内容超出模板页数上限(" + template.maxPages() + "页)，请精简内容或切换双页模板");
            }
            return new RenderedResume(docx, pdf, List.copyOf(issues), pages);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待简历排版资源超时", e);
        } finally {
            deleteRecursively(work.resolve("lo-profile"));
        }
    }

    private void buildDocx(ResumeDraft draft, ResumeTemplateRegistry.TemplateEntry template,
                           byte[] photoBytes, Path output) throws IOException {
        try (InputStream in = new ClassPathResource(template.baseResource()).getInputStream();
             XWPFDocument doc = new XWPFDocument(in)) {
            clearBody(doc);
            configurePage(doc, template);
            String color = template.color();
            ProfileData profile = draft.profileSnapshot();
            BasicInfo basics = profile == null ? null : profile.basics();
            addHeader(doc, basics, draft.targetRole(), color, photoBytes, template);

            DraftData data = draft.data();
            boolean hasDraftBlocks = data != null && data.blocks() != null && !data.blocks().isEmpty();
            boolean splitLayout = "T06".equals(template.id()) || "T07".equals(template.id());
            IBody left = doc, right = doc;
            if (splitLayout) {
                XWPFTable content = doc.createTable(1, 2); content.setWidth("100%"); content.removeBorders();
                var props = content.getCTTbl().getTblPr();
                var layout = props.isSetTblLayout() ? props.getTblLayout() : props.addNewTblLayout();
                layout.setType(STTblLayoutType.FIXED);
                boolean operations = "T06".equals(template.id());
                XWPFTableCell leftCell = content.getRow(0).getCell(0), rightCell = content.getRow(0).getCell(1);
                leftCell.setWidth(operations ? "3400" : "5170"); rightCell.setWidth(operations ? "5780" : "5170");
                content.setCellMargins(0, 110, 0, 110);
                left = leftCell; right = rightCell;
            }
            if (hasDraftBlocks) {
                for (DraftBlock block : data.blocks()) {
                    if (block == null || !block.visible()) continue;
                    String type = value(block.type()).toLowerCase();
                    IBody destination = splitLayout && !(type.contains("education") || type.contains("skill")) ? right : left;
                    List<DraftEntry> visibleEntries = safe(block.entries()).stream().filter(e -> e.visible() && e.confirmed()).toList();
                    if ("T07".equals(template.id()) && destination == right && visibleEntries.size() > 4) {
                        int pivot = (visibleEntries.size() + 1) / 2;
                        addBlock(left, new DraftBlock(block.id(), block.type(), block.title(), visibleEntries.subList(0, pivot), true), color, template.id());
                        addBlock(right, new DraftBlock(block.id(), block.type(), block.title(), visibleEntries.subList(pivot, visibleEntries.size()), true), color, template.id());
                    } else addBlock(destination, block, color, template.id());
                }
            } else {
                addConfirmedProfileSections(left, right, profile, color, template.id());
            }
            try (var out = Files.newOutputStream(output)) {
                doc.write(out);
            }
        }
    }

    private void configurePage(XWPFDocument doc, ResumeTemplateRegistry.TemplateEntry template) {
        var sec = doc.getDocument().getBody().getSectPr();
        if (sec == null) sec = doc.getDocument().getBody().addNewSectPr();
        var pgSz = sec.isSetPgSz() ? sec.getPgSz() : sec.addNewPgSz();
        pgSz.setW(BigInteger.valueOf(11906));
        pgSz.setH(BigInteger.valueOf(16838));
        var mar = sec.isSetPgMar() ? sec.getPgMar() : sec.addNewPgMar();
        int top = switch (template.id()) { case "T01" -> 1930; case "T03" -> 2320; case "T05" -> 580; case "T06" -> 1240; case "T07" -> 1510; default -> 760; };
        mar.setTop(BigInteger.valueOf(top));
        mar.setBottom(BigInteger.valueOf(740));
        mar.setLeft(BigInteger.valueOf("T03".equals(template.id()) ? 1720 : "T06".equals(template.id()) ? 1470 : 780));
        mar.setRight(BigInteger.valueOf("T06".equals(template.id()) ? 1260 : 780));
        // T04 deliberately has one flowing column: the source's fragmented column
        // sections caused compressed text and an empty trailing page.
        var cols = sec.isSetCols() ? sec.getCols() : sec.addNewCols();
        cols.setNum(BigInteger.ONE);
        var normal = doc.getStyles().getStyle("Normal");
        if (normal != null) {
            var rpr = normal.getCTStyle().isSetRPr() ? normal.getCTStyle().getRPr() : normal.getCTStyle().addNewRPr();
            var fonts = rpr.addNewRFonts();
            fonts.setAscii(FONT); fonts.setHAnsi(FONT); fonts.setEastAsia(FONT);
            var size = rpr.addNewSz();
            size.setVal(BigInteger.valueOf(20));
        }
    }

    private void clearBody(XWPFDocument doc) {
        var body = doc.getDocument().getBody();
        for (int i = body.sizeOfPArray() - 1; i >= 0; i--) body.removeP(i);
        for (int i = body.sizeOfTblArray() - 1; i >= 0; i--) body.removeTbl(i);
    }

    private void addHeader(XWPFDocument doc, BasicInfo basics, String role, String color, byte[] photo,
                           ResumeTemplateRegistry.TemplateEntry template) throws IOException {
        String name = value(basics == null ? null : basics.name());
        String contact = join(" · ", basics == null ? null : basics.phone(), basics == null ? null : basics.email(),
                basics == null ? null : basics.city());
        String portfolio = value(basics == null ? null : basics.portfolioUrl());
        String target = value(role);
        String id = template.id();
        boolean photoLeft = "T05".equals(id) || "T06".equals(id);
        boolean white = "T05".equals(id);
        XWPFTable table = doc.createTable(1, 2); table.setWidth("100%");
        var props = table.getCTTbl().getTblPr();
        var layout = props.isSetTblLayout() ? props.getTblLayout() : props.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        table.removeBorders();
        XWPFTableCell textCell = table.getRow(0).getCell(photoLeft ? 1 : 0);
        XWPFTableCell photoCell = table.getRow(0).getCell(photoLeft ? 0 : 1);
        photoCell.setWidth("T06".equals(id) ? "3400" : "1560");
        textCell.setWidth("T06".equals(id) ? "5780" : "8780");
        if (nonBlank(name)) addCellText(textCell, name, 23, white ? "FFFFFF" : "T07".equals(id) ? "40546F" : "T06".equals(id) ? "52634D" : color, true);
        if (nonBlank(target)) addCellText(textCell, target, 10, white ? "FFFFFF" : "555555", false);
        if (nonBlank(contact)) addCellText(textCell, contact, 10, white ? "FFFFFF" : "555555", false);
        if (nonBlank(portfolio)) addCellText(textCell, portfolio, 10, white ? "FFFFFF" : "555555", false);
        XWPFParagraph picture = photoCell.getParagraphs().get(0);
        picture.setAlignment(photoLeft ? ParagraphAlignment.CENTER : ParagraphAlignment.RIGHT);
        picture.setSpacingAfter(0);
        addPhoto(picture, photo);
        if ("T08".equals(id)) {
            XWPFParagraph accent = doc.createParagraph(); accent.setSpacingAfter(1);
            addDecoration(accent, id, "header-accent", 517, 14);
        }
    }

    private void addPhoto(XWPFParagraph p, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 8) return;
        BufferedImage image;
        try { image = ImageIO.read(new ByteArrayInputStream(bytes)); } catch (Exception e) { return; }
        if (image == null) return;
        double max = Units.toEMU(60);
        double ratio = Math.min(max / image.getWidth(), max / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        XWPFRun run = p.createRun();
        try {
            run.addPicture(new ByteArrayInputStream(bytes), pictureType(bytes), "photo", width, height);
        } catch (Exception ignored) {
            // An invalid optional photo cannot invalidate an otherwise usable text resume.
        }
    }

    private void addCellText(XWPFTableCell cell, String text, int size, String color, boolean bold) {
        XWPFParagraph first = cell.getParagraphs().get(0);
        XWPFParagraph p = first.getRuns().isEmpty() ? first : cell.addParagraph();
        p.setSpacingAfter(0); p.setSpacingBefore(0); addRun(p, text, size, color, bold);
    }

    private void addConfirmedProfileSections(IBody left, IBody right, ProfileData profile, String color, String templateId) {
        if (profile == null) return;
        List<Education> education = safe(profile.education()).stream().filter(this::confirmed).toList();
        if (!education.isEmpty()) {
            addSectionTitle(left, "教育背景", color, templateId);
            for (Education e : education) addEntry(left, join(" | ", e.school(), e.major(), e.degree()), join(" - ", e.startDate(), e.endDate()), e.notes(), color);
        }
        List<SkillItem> skills = safe(profile.skills()).stream().filter(this::confirmed).toList();
        if (!skills.isEmpty()) {
            addSectionTitle(left, "技能", color, templateId);
            for (SkillItem s : skills) addBullet(left, s.name(), "333333");
        }
        List<Experience> experiences = safe(profile.experiences()).stream().filter(Experience::confirmed).toList();
        if (!experiences.isEmpty()) {
            if ("T07".equals(templateId) && experiences.size() > 4) {
                int pivot = (experiences.size() + 1) / 2;
                addExperiences(left, experiences.subList(0, pivot), color, templateId);
                addExperiences(right, experiences.subList(pivot, experiences.size()), color, templateId);
            } else addExperiences(right, experiences, color, templateId);
        }
        List<Credential> credentials = safe(profile.credentials()).stream().filter(this::confirmed).toList();
        if (!credentials.isEmpty()) {
            addSectionTitle(right, "证书与荣誉", color, templateId);
            for (Credential c : credentials) addBullet(right, join("：", c.title(), c.description()), "333333");
        }
    }

    private void addExperiences(IBody body, List<Experience> experiences, String color, String templateId) {
        addSectionTitle(body, "项目与实践", color, templateId);
        for (Experience e : experiences) {
            addEntry(body, join(" | ", e.title(), e.organization(), e.role()), join(" - ", e.startDate(), e.endDate()), "", color);
            for (String detail : List.of(value(e.actions()), value(e.methods()), value(e.results()))) addBullet(body, detail, "333333");
            for (String link : safe(e.links())) addHyperlinkBullet(body, link, color);
        }
    }

    private void addBlock(IBody doc, DraftBlock block, String color, String templateId) {
        if ("basic".equalsIgnoreCase(value(block.type())) || "basics".equalsIgnoreCase(value(block.type()))) return;
        addSectionTitle(doc, valueOr(block.title(), labelFor(block.type())), color, templateId);
        for (DraftEntry entry : safe(block.entries())) {
            if (entry == null || !entry.visible() || !entry.confirmed()) continue;
            String heading = join(" | ", entry.title(), entry.subtitle());
            if (nonBlank(heading)) addEntry(doc, heading, "", "", color);
            for (String bullet : safe(entry.bullets())) addBullet(doc, bullet, "333333");
            for (String link : safe(entry.links())) addHyperlinkBullet(doc, link, color);
        }
    }

    private void addEntry(IBody doc, String heading, String date, String detail, String color) {
        if (!nonBlank(heading)) return;
        XWPFParagraph p = paragraph(doc); p.setSpacingBefore(2); p.setSpacingAfter(0); p.setKeepNext(true);
        addRun(p, heading, 10, "222222", true);
        if (nonBlank(date)) addRun(p, "  " + date, 10, "777777", false);
        if (nonBlank(detail)) addBullet(doc, detail, "333333");
    }

    private void addSectionTitle(IBody doc, String title, String color, String templateId) {
        if (!nonBlank(title)) return;
        XWPFParagraph p = paragraph(doc); p.setSpacingBefore(7); p.setSpacingAfter(2); p.setKeepNext(true);
        if ("T02".equals(templateId) || "T05".equals(templateId)) {
            String motif = title.contains("教育") ? "education" : title.contains("技能") ? "skills"
                    : title.contains("荣誉") || title.contains("证书") ? "credential" : "experience";
            addDecoration(p, templateId, motif, "T02".equals(templateId) ? 21 : 20, 16);
            addRun(p, "  " + title, 12, color, true);
        } else if ("T06".equals(templateId)) {
            addRun(p, title, 12, "52634D", true);
            XWPFParagraph accent = paragraph(doc); accent.setSpacingAfter(1); accent.setKeepNext(true);
            addDecoration(accent, templateId, "section", doc instanceof XWPFTableCell ? 141 : 277, 9);
        } else if ("T01".equals(templateId)) {
            addRun(p, title, 12, color, true);
            XWPFParagraph accent = paragraph(doc); accent.setSpacingAfter(1); accent.setKeepNext(true);
            addDecoration(accent, templateId, "section", 517, 8);
        } else if ("T08".equals(templateId)) {
            addDecoration(p, templateId, "section", 78, 3);
            addRun(p, "  " + title, 12, color, true);
        } else if ("T07".equals(templateId)) {
            addDecoration(p, templateId, "section", 34, 5);
            addRun(p, "  " + title, 12, "587398", true);
        } else {
            addRun(p, title, 12, color, true);
            CTPPr ppr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
            var borders = ppr.isSetPBdr() ? ppr.getPBdr() : ppr.addNewPBdr();
            CTBorder border = borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom();
            border.setVal(STBorder.SINGLE); border.setSz(BigInteger.valueOf(8)); border.setColor(color); border.setSpace(BigInteger.ONE);
        }
    }

    private void addDecoration(XWPFParagraph p, String templateId, String name, double width, double height) {
        try (InputStream source = new ClassPathResource("resume-templates/decorations/" + templateId + "/" + name + ".png").getInputStream()) {
            p.createRun().addPicture(source, Document.PICTURE_TYPE_PNG, "source-decoration-" + name,
                    Units.toEMU(width), Units.toEMU(height));
        } catch (Exception e) { throw new IllegalStateException("adapted source decoration unavailable: " + templateId + "/" + name, e); }
    }

    private void addBullet(IBody doc, String text, String color) {
        if (!nonBlank(text)) return;
        XWPFParagraph p = paragraph(doc); p.setIndentationLeft(260); p.setIndentationHanging(140); p.setSpacingAfter(1);
        addRun(p, "• ", 10, color, false); addRun(p, text.trim(), 10, color, false);
    }

    private void addHyperlinkBullet(IBody doc, String link, String color) {
        if (!nonBlank(link)) return;
        XWPFParagraph p = paragraph(doc); p.setIndentationLeft(260); p.setIndentationHanging(140); p.setSpacingAfter(1);
        addRun(p, "• ", 10, color, false);
        XWPFHyperlinkRun run = p.createHyperlinkRun(link.trim()); run.setFontFamily(FONT); run.setFontSize(10); run.setColor(color); run.setUnderline(UnderlinePatterns.SINGLE); run.setText(link.trim());
    }

    private XWPFParagraph paragraph(IBody body) {
        if (body instanceof XWPFDocument document) return document.createParagraph();
        if (body instanceof XWPFTableCell cell) {
            XWPFParagraph first = cell.getParagraphs().get(0);
            return first.getRuns().isEmpty() ? first : cell.addParagraph();
        }
        throw new IllegalArgumentException("unsupported resume content container");
    }

    private void addRule(XWPFDocument doc, String color, int size) {
        XWPFParagraph p = doc.createParagraph(); p.setSpacingAfter(1);
        CTPPr ppr = p.getCTP().addNewPPr(); var borders = ppr.addNewPBdr(); CTBorder border = borders.addNewBottom();
        border.setVal(STBorder.SINGLE); border.setSz(BigInteger.valueOf(size)); border.setColor(color); border.setSpace(BigInteger.ONE);
    }

    private void addRun(XWPFParagraph p, String text, int size, String color, boolean bold) {
        XWPFRun r = p.createRun(); r.setFontFamily(FONT); r.setFontSize(Math.max(10, size)); r.setColor(color); r.setBold(bold); r.setText(text == null ? "" : text);
    }

    private void convertToPdf(Path docx, Path work, List<String> issues) throws IOException, InterruptedException {
        if (!permits.tryAcquire(timeout.toSeconds(), TimeUnit.SECONDS)) throw new IOException("简历排版并发资源繁忙");
        Path profile = work.resolve("lo-profile"); Files.createDirectories(profile);
        try {
            Path conversionLog = work.resolve("conversion.log");
            Process process = new ProcessBuilder(sofficePath, "-env:UserInstallation=" + profile.toUri(), "--headless", "--convert-to", "pdf", "--outdir", work.toString(), docx.toString()).redirectErrorStream(true).redirectOutput(conversionLog.toFile()).start();
            boolean done = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!done) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); throw new IOException("简历排版超时"); }
            String output = Files.readString(conversionLog, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IOException("LibreOffice转换失败: " + output);
            if (!output.isBlank() && output.toLowerCase().contains("error")) issues.add("排版工具提示: " + output.trim());
        } finally { permits.release(); }
    }

    private void validateLinks(ResumeDraft draft) {
        ProfileData profile = draft.profileSnapshot();
        if (profile != null) for (Experience e : safe(profile.experiences())) for (String link : safe(e.links())) requireHttp(link);
        DraftData data = draft.data();
        if (data != null) for (DraftBlock b : safe(data.blocks())) for (DraftEntry e : safe(b.entries())) for (String link : safe(e.links())) requireHttp(link);
    }

    private static void requireHttp(String link) {
        if (!nonBlank(link)) return;
        try { URI uri = URI.create(link.trim()); String scheme = uri.getScheme(); if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) throw new IllegalArgumentException("仅支持 HTTP(S) 链接: " + link); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("链接必须是有效的 HTTP(S) 地址: " + link, e); }
    }

    private boolean confirmed(Education e) { return e != null && e.source() != null && e.source().confirmed(); }
    private boolean confirmed(SkillItem e) { return e != null && e.source() != null && e.source().confirmed(); }
    private boolean confirmed(Credential e) { return e != null && e.source() != null && e.source().confirmed(); }
    private static int pageCount(Path pdf) throws IOException { try (PDDocument document = PDDocument.load(pdf.toFile())) { return document.getNumberOfPages(); } }
    private static String safeName(String value) { return value == null || value.isBlank() ? "resume" : value.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private static String value(String text) { return text == null ? "" : text.trim(); }
    private static String valueOr(String text, String fallback) { return nonBlank(text) ? text.trim() : fallback; }
    private static boolean nonBlank(String text) { return text != null && !text.isBlank(); }
    private static String join(String separator, String... values) { return Arrays.stream(values).filter(Objects::nonNull).map(String::trim).filter(ResumeRenderService::nonBlank).reduce((a, b) -> a + separator + b).orElse(""); }
    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList(); }
    private static String labelFor(String type) { return switch (value(type).toLowerCase()) { case "education" -> "教育背景"; case "experience", "project", "internship" -> "项目与实践"; case "skill", "skills" -> "技能"; case "credential" -> "证书与荣誉"; default -> "经历"; }; }
    private static int pictureType(byte[] data) { return data.length > 1 && data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 ? Document.PICTURE_TYPE_JPEG : Document.PICTURE_TYPE_PNG; }
    private static void shade(XWPFTableCell cell, String color) { var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr(); CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd(); shd.setFill(color); }
    private static void deleteRecursively(Path path) { if (!Files.exists(path)) return; try (var stream = Files.walk(path)) { stream.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); } catch (IOException ignored) {} }

    public record RenderedResume(Path docx, Path pdf, List<String> layoutIssues, int pageCount) {}
}
