package com.aicampus.ai.service.knowledge.workspace;

import com.aicampus.ai.service.KnowledgeBaseService;
import com.aicampus.ai.service.knowledge.KnowledgeChunkRecord;
import com.aicampus.ai.service.knowledge.KnowledgeFileTextExtractionService;
import com.aicampus.ai.service.knowledge.KnowledgeObjectStorageService;
import com.aicampus.ai.service.knowledge.KnowledgeIngestionJobStore;
import com.aicampus.common.dto.KnowledgeAnswerClaim;
import com.aicampus.common.dto.KnowledgeAnswerRequest;
import com.aicampus.common.dto.KnowledgeAnswerResponse;
import com.aicampus.common.dto.KnowledgeCitation;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.KnowledgeDocumentRolesRequest;
import com.aicampus.common.dto.KnowledgeWorkspaceModels.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeCatalogService {
    private static final String SYSTEM = "system";
    private static final String METADATA = "DOCUMENT_METADATA";
    private static final String TOPIC = "TOPIC";
    private final KnowledgeBaseService knowledge;
    private final KnowledgeWorkspaceStore store;
    private final KnowledgeFileTextExtractionService extraction;
    private final KnowledgeObjectStorageService storage;
    private final ObjectMapper mapper;
    private final ResourcePatternResolver resources;
    private KnowledgeIngestionJobStore ingestionJobs;

    @Autowired(required = false)
    public void setIngestionJobs(KnowledgeIngestionJobStore ingestionJobs) { this.ingestionJobs = ingestionJobs; }

    public KnowledgeCatalogService(KnowledgeBaseService knowledge, KnowledgeWorkspaceStore store,
            KnowledgeFileTextExtractionService extraction, KnowledgeObjectStorageService storage,
            ObjectMapper mapper, ResourcePatternResolver resources) {
        this.knowledge = knowledge;
        this.store = store;
        this.extraction = extraction;
        this.storage = storage;
        this.mapper = mapper;
        this.resources = resources;
        knowledge.setWorkspaceStore(store);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void seedTopics() {
        try {
            for (var resource : resources.getResources("classpath*:/knowledge-topics/*.json")) {
                try (InputStream input = resource.getInputStream()) {
                    List<KnowledgeTopic> topics = mapper.readValue(input, new TypeReference<>() {});
                    for (KnowledgeTopic topic : topics) {
                        var existingTopic = store.get(TOPIC, topic.id(), SYSTEM, KnowledgeTopic.class);
                        if (existingTopic.isPresent()) {
                            if (!knowledge.exists(topic.documentId()) && !"DELETED".equals(metadata(topic.documentId()).status())) {
                                knowledge.saveDocument(topicDocument(existingTopic.get()));
                            }
                            continue;
                        }
                        if ("DELETED".equals(metadata(topic.documentId()).status())) continue;
                        if (knowledge.exists(topic.documentId())) {
                            if (topic.id().equals(metadata(topic.documentId()).topicId())) store.put(TOPIC, topic.id(), SYSTEM, topic);
                            continue;
                        }
                        validateTopic(topic);
                        store.put(METADATA, topic.documentId(), SYSTEM, new DocumentMetadata(topic.documentId(),
                                1, "PUBLISHED", null, null, "topic", List.of(), topic.role(), topic.skill(),
                                topic.difficulty(), "TOPIC", topic.id(), Instant.now()));
                        knowledge.saveDocument(topicDocument(topic));
                        store.put(TOPIC, topic.id(), SYSTEM, topic);
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load the authored Chinese knowledge topics", ex);
        }
    }

    public List<KnowledgeTopic> listTopics(String roleDirection, String skill, String difficulty,
            String contentType, String actorRole) {
        Set<String> readableIds = knowledge.readableDocuments(actorRole).stream().map(KnowledgeDocument::documentId)
                .collect(Collectors.toSet());
        return store.list(TOPIC, SYSTEM, KnowledgeTopic.class).stream()
                .filter(t -> readableIds.contains(t.documentId()))
                .filter(t -> matches(t.role(), normalizeDirection(roleDirection)))
                .filter(t -> contains(t.skill(), skill))
                .filter(t -> matches(normalizeDifficulty(t.difficulty()), normalizeDifficulty(difficulty)))
                .filter(t -> blank(contentType) || Set.of("TOPIC", "ALL", "CONCEPT", "EXAMPLE", "PRACTICE")
                        .contains(contentType.toUpperCase(Locale.ROOT)))
                .map(this::currentTopic)
                .sorted(Comparator.comparing(KnowledgeTopic::id)).toList();
    }

    public KnowledgeTopic topic(String id, String actorRole) {
        KnowledgeTopic topic = store.get(TOPIC, id, SYSTEM, KnowledgeTopic.class)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge topic not found"));
        requireReadable(topic.documentId(), actorRole);
        return currentTopic(topic);
    }

    private KnowledgeTopic currentTopic(KnowledgeTopic topic) {
        KnowledgeDocument document = knowledge.document(topic.documentId());
        DocumentMetadata meta = metadata(topic.documentId());
        // Edited source content is authoritative; old authored sections must never survive a new publication.
        if (meta.revision() > topic.version()) {
            return new KnowledgeTopic(topic.id(), topic.role(), topic.skill(), document.title(),
                    excerpt(document.content(), 160), document.content(), "", "", topic.practiceType(),
                    topic.difficulty(), topic.estimatedMinutes(), topic.prerequisites(), document.source(),
                    topic.sourceUrl(), topic.applicableVersion(), topic.checkedAt(), topic.documentId(),
                    meta.revision(), meta.status(), headings(document.content()), topic.createdAt(), meta.updatedAt());
        }
        return new KnowledgeTopic(topic.id(), topic.role(), topic.skill(), document.title(), topic.summary(),
                topic.content(), topic.example(), topic.practicePrompt(), topic.practiceType(), topic.difficulty(),
                topic.estimatedMinutes(), topic.prerequisites(), topic.source(), topic.sourceUrl(),
                topic.applicableVersion(), topic.checkedAt(), topic.documentId(), meta.revision(), meta.status(),
                topic.headings(), topic.createdAt(), meta.updatedAt());
    }

    public KnowledgeLibraryDocument library(String id, String actorRole) {
        requireReadable(id, actorRole);
        KnowledgeDocument document = knowledge.document(id);
        DocumentMetadata meta = metadata(id);
        List<KnowledgeSourceLocation> locations = knowledge.documentChunks(id).stream()
                .map(chunk -> location(chunk, document.content(), meta.pages())).toList();
        return new KnowledgeLibraryDocument(id, document.title(), document.content(), document.source(),
                document.category(), document.tags(), meta.revision(), meta.status(), !blank(meta.originalObjectKey()),
                meta.pages(), locations, document.roles());
    }

    public KnowledgeAnswerResponse search(KnowledgeQuery query, String actorId, String actorRole) {
        if (query == null || blank(query.query())) throw new IllegalArgumentException("Enter a knowledge question or keyword");
        if (query.query().length() > 2000) throw new IllegalArgumentException("Knowledge query must not exceed 2000 characters");
        Set<String> filtered = null;
        if (!blank(query.roleDirection()) || !blank(query.skill()) || !blank(query.difficulty()) || !blank(query.contentType())) {
            filtered = knowledge.readableDocuments(actorRole).stream()
                    .filter(d -> matchesDocument(d, query))
                    .map(KnowledgeDocument::documentId).collect(Collectors.toSet());
        }
        String filterKey = KnowledgeBaseService.fingerprint(query.toString());
        KnowledgeAnswerResponse response = knowledge.answer(new KnowledgeAnswerRequest(query.query(), actorRole, 5,
                query.useAi()), actorId, filtered, filterKey);
        if (Boolean.TRUE.equals(query.useAi()) && !response.citations().isEmpty()) {
            return authoredExplanation(response, actorRole);
        }
        return response;
    }

    private boolean matchesDocument(KnowledgeDocument doc, KnowledgeQuery query) {
        DocumentMetadata meta = metadata(doc.documentId());
        String direction = normalizeDirection(query.roleDirection());
        List<String> labels = new ArrayList<>(doc.tags() == null ? List.of() : doc.tags());
        labels.add(doc.category());
        labels.add(doc.title());
        if (!blank(direction)) {
            boolean directionMatches = !blank(meta.roleDirection())
                    ? matches(normalizeDirection(meta.roleDirection()), direction)
                    : labels.stream().anyMatch(label -> matches(normalizeDirection(label), direction));
            if (!directionMatches) return false;
        }
        if (!blank(query.skill()) && !contains(String.join(" ", labels.stream().filter(v -> v != null).toList())
                + " " + (meta.skill() == null ? "" : meta.skill()), query.skill())) return false;
        String difficulty = normalizeDifficulty(meta.difficulty());
        if (blank(difficulty)) difficulty = labels.stream().map(KnowledgeCatalogService::normalizeDifficulty)
                .filter(v -> Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(v == null ? "" : v))
                .findFirst().orElse(null);
        if (!matches(difficulty, normalizeDifficulty(query.difficulty()))) return false;
        String kind = query.contentType();
        return blank(kind) || "ALL".equalsIgnoreCase(kind) || matches(meta.contentType(), kind)
                || ("TOPIC".equals(meta.contentType()) && Set.of("CONCEPT", "EXAMPLE", "PRACTICE").contains(kind.toUpperCase(Locale.ROOT)));
    }

    private KnowledgeAnswerResponse authoredExplanation(KnowledgeAnswerResponse result, String actorRole) {
        String revision = knowledge.permissionVersion();
        List<KnowledgeCitation> citations = new ArrayList<>();
        List<KnowledgeAnswerClaim> claims = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        List<String> ids = result.citations().stream().map(KnowledgeCitation::documentId).distinct().limit(5).toList();
        List<ExplanationSource> sources = new ArrayList<>();
        for (String id : ids) {
            DocumentMetadata meta = metadata(id);
            if (!knowledge.readable(id, actorRole)) continue;
            boolean modelGrounded = result.claims().stream().anyMatch(claim -> claim.citationIds().stream()
                    .anyMatch(chunkId -> result.citations().stream().anyMatch(c -> id.equals(c.documentId()) && chunkId.equals(c.chunkId()))));
            if (!modelGrounded && !knowledge.hasQueryEvidence(id, result.query())) continue;
            KnowledgeTopic topic = blank(meta.topicId()) ? null : topic(meta.topicId(), actorRole);
            List<GroundedSection> sections = new ArrayList<>();
            if (topic != null && meta.revision() == topic.version() && !blank(topic.example()) && !blank(topic.practicePrompt())) {
                groundedSection(id, "直接结论", topic.summary(), sections);
                groundedSection(id, "通俗解释", topic.content(), sections);
                groundedSection(id, "资料中的例子", topic.example(), sections);
                groundedSection(id, "可做的练习", topic.practicePrompt(), sections);
                groundedSection(id, "来源与适用范围", "适用版本：" + topic.applicableVersion()
                        + "\n核对日期：" + topic.checkedAt(), sections);
            } else {
                result.citations().stream().filter(c -> id.equals(c.documentId()))
                        .forEach(c -> groundedSection(id, "原文摘录", c.snippet(), sections));
            }
            if (!sections.isEmpty()) sources.add(new ExplanationSource(knowledge.document(id), meta, topic, sections));
        }
        // Reserve one evidence slot per source before filling further authored sections.
        Map<String, List<GroundedSection>> selected = new LinkedHashMap<>();
        for (int sectionIndex = 0; sectionIndex < 5; sectionIndex++) {
            for (ExplanationSource source : sources) {
                if (sectionIndex >= source.sections().size()) continue;
                GroundedSection section = source.sections().get(sectionIndex);
                int index = citationIndex(citations, section.chunk().chunkId());
                if (index < 0 && citations.size() >= 5) continue;
                if (index < 0) citations.add(sectionCitation(section.chunk(), source.metadata()));
                selected.computeIfAbsent(source.document().documentId(), ignored -> new ArrayList<>()).add(section);
            }
        }
        if (sources.size() > 1) answer.append("> 不同来源分别展示，请核对适用版本与差异；以下内容未合并为统一结论。\n\n");
        for (ExplanationSource source : sources) {
            List<GroundedSection> sections = selected.get(source.document().documentId());
            if (sections == null) continue;
            answer.append("### ").append(source.document().title()).append("\n\n> 来源：")
                    .append(source.document().source()).append("；资料版本：v").append(source.metadata().revision());
            if (source.topic() != null) answer.append("；适用版本：").append(source.topic().applicableVersion())
                    .append("；核对日期：").append(source.topic().checkedAt());
            answer.append("\n\n");
            if (source.topic() == null || blank(source.topic().example()))
                answer.append("> 当前资料没有配套的自编讲解和例子，仅展示可定位的原文摘录。\n\n");
            for (GroundedSection section : sections) appendGroundedSection(answer, claims, citations, section);
        }
        if (claims.isEmpty()) return result;
        if (!revision.equals(knowledge.permissionVersion())) {
            return new KnowledgeAnswerResponse(result.query(), "资料已更新或权限已变化，请重新查询。", List.of(), true,
                    "local-authored-topics", Instant.now(), result.retrievalMode(), "RETRIEVAL_ONLY", "PERMISSIONS_CHANGED",
                    result.algorithmVersion(), knowledge.permissionVersion(), List.of(), result.inputFingerprint(), result.metadata());
        }
        return new KnowledgeAnswerResponse(result.query(), answer.toString(), List.copyOf(citations), result.mocked(),
                "authored-topics", Instant.now(), result.retrievalMode(), "GROUNDED_EXPLANATION", "VERIFIED",
                result.algorithmVersion(), revision, List.copyOf(claims), result.inputFingerprint(), result.metadata());
    }

    private void groundedSection(String documentId, String heading, String text, List<GroundedSection> sections) {
        if (blank(text)) return;
        KnowledgeChunkRecord chunk = knowledge.documentChunks(documentId).stream()
                .filter(c -> c.text().contains(text)).findFirst().orElse(null);
        if (chunk != null) sections.add(new GroundedSection(heading, text, chunk));
    }

    private void appendGroundedSection(StringBuilder answer, List<KnowledgeAnswerClaim> claims,
            List<KnowledgeCitation> citations, GroundedSection section) {
        int index = citationIndex(citations, section.chunk().chunkId());
        if (index < 0) return;
        answer.append("#### ").append(section.heading()).append("\n\n").append(section.text())
                .append(" [").append(index + 1).append("]\n\n");
        claims.add(new KnowledgeAnswerClaim(section.text(), List.of(section.chunk().chunkId()), section.text()));
    }

    private static int citationIndex(List<KnowledgeCitation> citations, String chunkId) {
        for (int i = 0; i < citations.size(); i++) if (citations.get(i).chunkId().equals(chunkId)) return i;
        return -1;
    }

    private static KnowledgeCitation sectionCitation(KnowledgeChunkRecord chunk, DocumentMetadata meta) {
        Integer page = null;
        if (chunk.startOffset() != null) for (KnowledgePage location : meta.pages()) {
            if (chunk.startOffset() >= location.startOffset() && chunk.startOffset() < location.endOffset()) {
                page = location.pageNumber(); break;
            }
        }
        return new KnowledgeCitation(chunk.documentId(), chunk.chunkId(), chunk.title(), chunk.source(),
                100, chunk.text(), chunk.chunkIndex(), chunk.startOffset(), chunk.endOffset(), chunk.heading(), chunk.roles(), meta.revision(), page);
    }

    private record GroundedSection(String heading, String text, KnowledgeChunkRecord chunk) {}
    private record ExplanationSource(KnowledgeDocument document, DocumentMetadata metadata, KnowledgeTopic topic,
            List<GroundedSection> sections) {}

    public OriginalFile readOriginal(String id, String actorRole) {
        requireReadable(id, actorRole);
        DocumentMetadata meta = metadata(id);
        if (blank(meta.originalObjectKey())) throw new IllegalArgumentException("The original file is unavailable; extracted text remains readable");
        try (InputStream stream = storage.read(meta.originalObjectKey(), null, null).stream()) {
            byte[] bytes = stream.readAllBytes();
            requireReadable(id, actorRole);
            return new OriginalFile(bytes, mimeType(meta.fileFormat()), meta.originalFileName());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read the knowledge original", ex);
        }
    }

    public synchronized KnowledgeLibraryDocument adminEdit(String id, KnowledgePublicationRequest request, String actorId) {
        if (request == null || blank(request.title()) || blank(request.content())) {
            throw new IllegalArgumentException("Knowledge title and content are required");
        }
        if (request.title().length() > 255 || request.content().length() > 120_000
                || (!blank(request.source()) && request.source().length() > 128)) {
            throw new IllegalArgumentException("Knowledge title or content exceeds its supported size");
        }
        boolean creating = blank(id) || "new".equals(id);
        String documentId = creating ? "KB-" + UUID.randomUUID().toString().substring(0, 8) : id;
        KnowledgeDocument previous = creating ? null : knowledge.document(documentId);
        DocumentMetadata meta = creating ? emptyMetadata(documentId) : metadata(documentId);
        if (!creating && (request.expectedRevision() == null || request.expectedRevision() != meta.revision())) {
            throw new IllegalStateException("Knowledge version conflict; retain your changes and reload the current version");
        }
        boolean textChanged = previous == null || !previous.content().equals(request.content());
        List<KnowledgePage> pages = textChanged ? List.of() : meta.pages();
        List<String> roles = normalizedRoles(request.roles());
        if (previous != null) saveVersion(previous, meta);
        DocumentMetadata updated = new DocumentMetadata(documentId, creating ? 1 : meta.revision() + 1, "DRAFT",
                meta.originalObjectKey(), meta.originalFileName(), meta.fileFormat(), pages, meta.roleDirection(),
                meta.skill(), meta.difficulty(), meta.contentType(), meta.topicId(), Instant.now());
        store.put(METADATA, documentId, SYSTEM, updated);
        KnowledgeDocument document = new KnowledgeDocument(documentId, request.title().trim(), request.content(),
                blank(request.category()) ? "general" : request.category().trim(),
                blank(request.source()) ? "管理员自编资料" : request.source().trim(),
                request.tags() == null ? List.of() : request.tags(), roles,
                previous == null ? actorId : previous.createdBy(), previous == null ? LocalDateTime.now() : previous.createdAt());
        knowledge.saveDocument(document);
        return library(documentId, "ADMIN");
    }

    public synchronized KnowledgeLibraryDocument publish(String id, boolean published) {
        knowledge.document(id);
        DocumentMetadata meta = metadata(id);
        if (published && blank(knowledge.document(id).content())) throw new IllegalArgumentException("Cannot publish an empty document");
        store.put(METADATA, id, SYSTEM, new DocumentMetadata(id, meta.revision(), published ? "PUBLISHED" : "UNPUBLISHED",
                meta.originalObjectKey(), meta.originalFileName(), meta.fileFormat(), meta.pages(), meta.roleDirection(),
                meta.skill(), meta.difficulty(), meta.contentType(), meta.topicId(), Instant.now()));
        knowledge.invalidateCaches();
        return library(id, "ADMIN");
    }

    public synchronized KnowledgeLibraryDocument reparse(String id) {
        OriginalFile original = readOriginal(id, "ADMIN");
        var extracted = extraction.extract(original.bytes(), original.fileName());
        KnowledgeDocument previous = knowledge.document(id);
        DocumentMetadata meta = metadata(id);
        saveVersion(previous, meta);
        store.put(METADATA, id, SYSTEM, new DocumentMetadata(id, meta.revision() + 1, "DRAFT", meta.originalObjectKey(),
                meta.originalFileName(), extracted.fileFormat(), extracted.pages().stream()
                        .map(p -> new KnowledgePage(p.pageNumber(), p.startOffset(), p.endOffset())).toList(),
                meta.roleDirection(), meta.skill(), meta.difficulty(), meta.contentType(), meta.topicId(), Instant.now()));
        knowledge.saveDocument(new KnowledgeDocument(id, previous.title(), extracted.text(), previous.category(), previous.source(),
                previous.tags(), previous.roles(), previous.createdBy(), previous.createdAt()));
        return library(id, "ADMIN");
    }

    public synchronized void recordImportedDocument(String id, String objectKey, String fileName, String fileFormat,
            List<KnowledgeFileTextExtractionService.PageSpan> pages, String storageStatus) {
        String key = "STORED".equals(storageStatus) ? objectKey : null;
        store.put(METADATA, id, SYSTEM, new DocumentMetadata(id, 1, "DRAFT", key, fileName, fileFormat,
                pages.stream().map(p -> new KnowledgePage(p.pageNumber(), p.startOffset(), p.endOffset())).toList(),
                null, null, null, "DOCUMENT", null, Instant.now()));
        knowledge.invalidateCaches();
    }

    public DocumentMetadata metadata(String id) {
        return store.get(METADATA, id, SYSTEM, DocumentMetadata.class).orElseGet(() -> legacyMetadata(id));
    }

    private void saveVersion(KnowledgeDocument document, DocumentMetadata meta) {
        store.insert("DOCUMENT_VERSION", document.documentId() + ":v" + meta.revision(), SYSTEM,
                Map.of("document", document, "metadata", meta));
    }

    private DocumentMetadata legacyMetadata(String id) {
        if (ingestionJobs != null) {
            var original = ingestionJobs.list(null, 200).stream().filter(job -> id.equals(job.documentId()))
                    .filter(job -> "STORED".equals(job.storageStatus()) && !blank(job.objectKey())).findFirst();
            if (original.isPresent()) {
                var job = original.get();
                return new DocumentMetadata(id, 1, "LEGACY", job.objectKey(), job.fileName(), job.fileFormat(),
                        List.of(), null, null, null, "DOCUMENT", null, job.updatedAt());
            }
        }
        return emptyMetadata(id);
    }

    private DocumentMetadata emptyMetadata(String id) {
        return new DocumentMetadata(id, 1, "LEGACY", null, null, null, List.of(), null, null, null, "DOCUMENT", null, Instant.EPOCH);
    }

    private void requireReadable(String id, String role) {
        if (!knowledge.readable(id, role)) throw new IllegalArgumentException("Knowledge document is unavailable or access is not allowed");
    }

    private KnowledgeDocument topicDocument(KnowledgeTopic topic) {
        String content = "# " + topic.title() + "\n\n## 直接结论\n" + topic.summary() + "\n\n## 通俗解释\n" + topic.content()
                + "\n\n## 资料中的例子\n" + topic.example() + "\n\n## 可做的练习\n" + topic.practicePrompt()
                + "\n\n## 来源与适用范围\n" + topic.source() + "\n" + topic.sourceUrl()
                + "\n适用版本：" + topic.applicableVersion() + "\n核对日期：" + topic.checkedAt();
        return new KnowledgeDocument(topic.documentId(), topic.title(), content, "topic", topic.source(),
                List.of(topic.role(), topic.skill(), "中文专题"), List.of("STUDENT", "ADMIN"), SYSTEM, LocalDateTime.of(2026, 10, 6, 8, 0));
    }

    private static KnowledgeSourceLocation location(KnowledgeChunkRecord chunk, String content, List<KnowledgePage> pages) {
        Integer start = chunk.startOffset(), end = chunk.endOffset();
        if (start == null || end == null || start < 0 || end > content.length() || !content.substring(start, end).equals(chunk.text())) {
            int found = content.indexOf(chunk.text());
            start = found < 0 ? null : found;
            end = found < 0 ? null : found + chunk.text().length();
        }
        Integer page = null;
        if (start != null) for (KnowledgePage span : pages) if (start >= span.startOffset() && start < span.endOffset()) {
            page = span.pageNumber(); break;
        }
        return new KnowledgeSourceLocation(chunk.chunkId(), chunk.chunkIndex(), chunk.heading(), start, end, page, chunk.text());
    }

    private static void validateTopic(KnowledgeTopic topic) {
        if (blank(topic.id()) || blank(topic.content()) || blank(topic.sourceUrl()) || blank(topic.checkedAt())
                || topic.estimatedMinutes() <= 0 || topic.prerequisites() == null) {
            throw new IllegalArgumentException("A topic requires content, references, check date, duration and prerequisites");
        }
    }

    private static List<String> normalizedRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) return List.of("ADMIN");
        List<String> result = roles.stream().filter(r -> r != null && !r.isBlank())
                .map(r -> r.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        if (result.isEmpty()) return List.of("ADMIN");
        if (result.stream().anyMatch(r -> !Set.of("STUDENT", "COMPANY", "ADMIN", "ALL").contains(r))) {
            throw new IllegalArgumentException("Unsupported knowledge reader role");
        }
        return result;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean matches(String value, String filter) { return blank(filter) || (value != null && value.equalsIgnoreCase(filter)); }
    private static boolean contains(String value, String filter) { return blank(filter) || (value != null && value.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT))); }
    private static String excerpt(String text, int max) { return text.length() <= max ? text : text.substring(0, max) + "..."; }
    private static List<String> headings(String text) { return text.lines().filter(l -> l.matches("^#{1,6} .*" )).map(l -> l.replaceFirst("^#{1,6} ", "")).toList(); }
    public static String normalizeDirection(String value) {
        if (blank(value)) return value;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches(".*(?<![A-Z])JAVA(?![A-Z]).*") || normalized.contains("BACKEND")
                || normalized.contains("BACK-END") || normalized.contains("后端")) return "JAVA";
        if (normalized.contains("FRONT") || normalized.contains("前端")
                || normalized.matches(".*(?<![A-Z])(JAVASCRIPT|TYPESCRIPT|HTML|CSS|VUE|REACT)(?![A-Z]).*")) return "FRONTEND";
        if (normalized.contains("OPERAT") || normalized.contains("运营")) return "OPERATIONS";
        return normalized;
    }

    private static String normalizeDifficulty(String value) {
        if (blank(value)) return value;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BASIC", "BEGINNER", "基础", "入门", "初级" -> "BEGINNER";
            case "INTERMEDIATE", "中级", "进阶" -> "INTERMEDIATE";
            case "ADVANCED", "高级" -> "ADVANCED";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }
    private static String mimeType(String format) {
        return switch (format == null ? "" : format) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "md", "txt" -> "text/plain;charset=UTF-8";
            default -> "application/octet-stream";
        };
    }

    public record DocumentMetadata(String documentId, int revision, String status, String originalObjectKey,
            String originalFileName, String fileFormat, List<KnowledgePage> pages, String roleDirection,
            String skill, String difficulty, String contentType, String topicId, Instant updatedAt) {}
    public record OriginalFile(byte[] bytes, String contentType, String fileName) {}
}
