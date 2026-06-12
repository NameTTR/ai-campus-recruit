package com.aicampus.ai.service;

import com.aicampus.common.dto.AiSearchResponse;
import com.aicampus.common.dto.AiSearchResult;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.KnowledgeDocumentRequest;
import com.aicampus.common.dto.KnowledgeSearchRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {
    private final ConcurrentMap<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultDocuments() {
        seed(new KnowledgeDocument(
                "KB-DEMO-001",
                "Campus recruitment Java backend interview guide",
                "Focus on Spring Boot layering, MySQL indexes, Redis cache consistency, Gateway routing, RocketMQ async delivery events, and three-VM deployment troubleshooting.",
                "interview",
                "seed",
                List.of("Java", "Spring Boot", "MySQL", "Redis", "RocketMQ"),
                List.of("STUDENT", "COMPANY", "ADMIN"),
                "system",
                LocalDateTime.now().minusDays(2)));
        seed(new KnowledgeDocument(
                "KB-DEMO-002",
                "Resume evidence checklist",
                "A strong campus resume should connect every claim to project ownership, API behavior, measurable latency, data volume, screenshots, tests, and deployment proof.",
                "resume",
                "seed",
                List.of("resume", "evidence", "metrics"),
                List.of("STUDENT", "ADMIN"),
                "system",
                LocalDateTime.now().minusDays(1)));
        seed(new KnowledgeDocument(
                "KB-DEMO-003",
                "Company candidate screening playbook",
                "Screening should combine delivery status, parsed resume quality, skill overlap, interview risk questions, and auditable AI recommendation records.",
                "screening",
                "seed",
                List.of("screening", "AI", "audit"),
                List.of("COMPANY", "ADMIN"),
                "system",
                LocalDateTime.now().minusHours(12)));
    }

    public KnowledgeDocument create(KnowledgeDocumentRequest request, String createdBy) {
        KnowledgeDocument document = new KnowledgeDocument(
                "KB-" + UUID.randomUUID().toString().substring(0, 8),
                valueOr(request == null ? null : request.title(), "Untitled knowledge"),
                valueOr(request == null ? null : request.content(), ""),
                valueOr(request == null ? null : request.category(), "general"),
                valueOr(request == null ? null : request.source(), "manual"),
                cleanList(request == null ? null : request.tags(), List.of("general")),
                normalizeRoles(request == null ? null : request.roles()),
                valueOr(createdBy, "system"),
                LocalDateTime.now());
        documents.put(document.documentId(), document);
        return document;
    }

    public List<KnowledgeDocument> list(String keyword, String role, Integer limit) {
        String query = valueOr(keyword, "").toLowerCase(Locale.ROOT);
        String normalizedRole = normalizeRole(role);
        int normalizedLimit = limit == null ? 20 : Math.max(1, Math.min(100, limit));
        return documents.values().stream()
                .filter(document -> canRead(document, normalizedRole))
                .filter(document -> query.isBlank() || documentText(document).contains(query))
                .sorted(Comparator.comparing(KnowledgeDocument::createdAt).reversed()
                        .thenComparing(KnowledgeDocument::documentId))
                .limit(normalizedLimit)
                .toList();
    }

    public AiSearchResponse search(KnowledgeSearchRequest request) {
        String query = valueOr(request == null ? null : request.query(), "");
        String role = normalizeRole(request == null ? null : request.role());
        int limit = request == null || request.limit() == null ? 8 : Math.max(1, Math.min(20, request.limit()));
        List<String> tokens = tokens(query);
        List<AiSearchResult> results = documents.values().stream()
                .filter(document -> canRead(document, role))
                .map(document -> toSearchResult(document, query, tokens))
                .filter(result -> query.isBlank() || result.score() > 0)
                .sorted(Comparator.comparing(AiSearchResult::score).reversed()
                        .thenComparing(AiSearchResult::title))
                .limit(limit)
                .toList();
        return new AiSearchResponse(query, results, Instant.now());
    }

    private AiSearchResult toSearchResult(KnowledgeDocument document, String query, List<String> tokens) {
        int score = score(document, query, tokens);
        return new AiSearchResult(
                document.documentId(),
                "knowledge",
                document.title(),
                document.createdBy(),
                truncate(document.content(), 180),
                score,
                highlights(document, query, tokens));
    }

    private int score(KnowledgeDocument document, String query, List<String> tokens) {
        if (query.isBlank()) {
            return 55;
        }
        String text = documentText(document);
        int score = text.contains(query.toLowerCase(Locale.ROOT)) ? 45 : 0;
        for (String token : tokens) {
            if (text.contains(token)) {
                score += token.length() > 4 ? 18 : 12;
            }
        }
        if (document.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
            score += 20;
        }
        return Math.min(100, score);
    }

    private List<String> highlights(KnowledgeDocument document, String query, List<String> tokens) {
        List<String> highlights = new ArrayList<>();
        if (!query.isBlank() && document.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
            highlights.add("Title matches query: " + document.title());
        }
        String text = documentText(document);
        for (String token : tokens) {
            if (text.contains(token)) {
                highlights.add("Matched knowledge term: " + token);
            }
            if (highlights.size() >= 3) {
                return highlights;
            }
        }
        if (highlights.isEmpty()) {
            highlights.add(truncate(document.content(), 96));
        }
        return highlights;
    }

    private void seed(KnowledgeDocument document) {
        documents.putIfAbsent(document.documentId(), document);
    }

    private boolean canRead(KnowledgeDocument document, String role) {
        return role == null || document.roles().contains(role) || document.roles().contains("ALL");
    }

    private String documentText(KnowledgeDocument document) {
        return String.join(" ",
                valueOr(document.title(), ""),
                valueOr(document.content(), ""),
                valueOr(document.category(), ""),
                valueOr(document.source(), ""),
                String.join(" ", cleanList(document.tags(), List.of())))
                .toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeRoles(List<String> roles) {
        List<String> normalized = cleanList(roles, List.of("ALL")).stream()
                .map(this::normalizeRole)
                .filter(role -> role != null && !role.isBlank())
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("ALL") : normalized;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> cleanList(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<String> clean = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        return clean.isEmpty() ? fallback : clean;
    }

    private List<String> tokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,;|]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
    }

    private static String truncate(String value, int maxLength) {
        String safe = valueOr(value, "");
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
