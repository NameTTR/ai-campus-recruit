package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeDocument;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryKnowledgeBaseStore implements KnowledgeBaseStore {
    private final ConcurrentMap<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, KnowledgeChunkRecord> chunks = new ConcurrentHashMap<>();

    @Override
    public void save(KnowledgeDocument document, List<KnowledgeChunkRecord> newChunks) {
        if (document == null) {
            return;
        }
        documents.put(document.documentId(), document);
        chunks.entrySet().removeIf(entry -> document.documentId().equals(entry.getValue().documentId()));
        if (newChunks != null) {
            for (KnowledgeChunkRecord chunk : newChunks) {
                if (chunk != null) {
                    chunks.put(chunk.chunkId(), chunk);
                }
            }
        }
    }

    @Override
    public KnowledgeDocument updateRoles(String documentId, List<String> roles) {
        if (documentId == null || documentId.isBlank()) {
            return null;
        }
        KnowledgeDocument updated = documents.computeIfPresent(documentId, (ignored, document) -> new KnowledgeDocument(
                document.documentId(),
                document.title(),
                document.content(),
                document.category(),
                document.source(),
                document.tags(),
                roles,
                document.createdBy(),
                document.createdAt()));
        if (updated == null) {
            return null;
        }
        chunks.replaceAll((ignored, chunk) -> Objects.equals(documentId, chunk.documentId())
                ? new KnowledgeChunkRecord(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.chunkIndex(),
                chunk.title(),
                chunk.text(),
                chunk.category(),
                chunk.source(),
                chunk.tags(),
                roles,
                chunk.createdBy(),
                chunk.createdAt(),
                chunk.embedding())
                : chunk);
        return updated;
    }

    @Override
    public boolean delete(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return false;
        }
        KnowledgeDocument removed = documents.remove(documentId);
        chunks.entrySet().removeIf(entry -> documentId.equals(entry.getValue().documentId()));
        return removed != null;
    }

    @Override
    public List<KnowledgeDocument> listDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(KnowledgeDocument::createdAt).reversed()
                        .thenComparing(KnowledgeDocument::documentId))
                .toList();
    }

    @Override
    public List<KnowledgeChunkRecord> listChunks() {
        return chunks.values().stream()
                .sorted(Comparator.comparing(KnowledgeChunkRecord::createdAt).reversed()
                        .thenComparing(KnowledgeChunkRecord::chunkIndex)
                        .thenComparing(KnowledgeChunkRecord::chunkId))
                .toList();
    }
}
