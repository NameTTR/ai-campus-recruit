package com.aicampus.ai.service.knowledge;

import com.aicampus.ai.service.KnowledgeBaseService;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeFileIngestionService implements DisposableBean {
    private static final Set<String> SUPPORTED_FORMATS = Set.of("txt", "md", "pdf", "doc", "docx");

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeFileTextExtractionService textExtractionService;
    private final KnowledgeObjectStorageService objectStorageService;
    private final KnowledgeIngestionJobStore jobStore;
    private final KnowledgeBaseProperties properties;
    private final ExecutorService executor;

    public KnowledgeFileIngestionService(
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeFileTextExtractionService textExtractionService,
            KnowledgeObjectStorageService objectStorageService,
            KnowledgeIngestionJobStore jobStore,
            KnowledgeBaseProperties properties) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.textExtractionService = textExtractionService;
        this.objectStorageService = objectStorageService;
        this.jobStore = jobStore;
        this.properties = properties;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "knowledge-file-ingestion-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public KnowledgeFileIngestionJob submit(
            MultipartFile file,
            String title,
            String category,
            String source,
            String tags,
            String roles,
            String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Knowledge file is required");
        }

        String fileName = safeFileName(file.getOriginalFilename());
        String extension = KnowledgeFileTextExtractionService.extension(fileName);
        if (!SUPPORTED_FORMATS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported knowledge file format: " + extension);
        }
        long maxBytes = properties.getIngestion().getMaxFileBytes();
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Knowledge file exceeds max size " + maxBytes + " bytes");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to read knowledge file: " + safeMessage(ex), ex);
        }
        String sha256 = sha256(bytes);
        KnowledgeFileIngestionJob reusable = jobStore.findReusableBySha256(sha256);

        Instant now = Instant.now();
        KnowledgeFileIngestionJob job = new KnowledgeFileIngestionJob(
                "KBI-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                fileName,
                extension,
                bytes.length,
                sha256,
                valueOr(title, titleFromFileName(fileName)),
                valueOr(category, "uploaded"),
                valueOr(source, "file:" + fileName),
                KnowledgeIngestionStatuses.UPLOADED,
                "Knowledge file uploaded; waiting for parser",
                null,
                null,
                null,
                0,
                0,
                null,
                valueOr(createdBy, "system"),
                now,
                now);

        KnowledgeObjectStorageService.StoredKnowledgeObject storedObject =
                objectStorageService.store(job.jobId(), fileName, file.getContentType(), bytes);
        job = KnowledgeIngestionJobMutations.withStorage(
                job,
                storedObject.objectKey(),
                storedObject.storageProvider(),
                storedObject.storageStatus());
        jobStore.create(job);

        if (reusable != null) {
            KnowledgeFileIngestionJob duplicate = KnowledgeIngestionJobMutations.withStatus(
                    job,
                    KnowledgeIngestionStatuses.DUPLICATE,
                    "Duplicate file detected; reused existing ingestion job " + reusable.jobId(),
                    reusable.documentId(),
                    reusable.chunkCount());
            return jobStore.update(duplicate);
        }

        KnowledgeFileIngestionJob queuedJob = job;
        executor.submit(() -> runIngestion(
                queuedJob,
                bytes,
                valueOr(title, titleFromFileName(fileName)),
                valueOr(category, "uploaded"),
                valueOr(source, "file:" + fileName),
                splitCsv(tags, List.of("uploaded", extension)),
                splitCsv(roles, List.of("ADMIN"))));
        return queuedJob;
    }

    public List<KnowledgeFileIngestionJob> list(String status, Integer limit) {
        int normalizedLimit = limit == null ? 20 : Math.max(1, Math.min(200, limit));
        return jobStore.list(status, normalizedLimit);
    }

    private void runIngestion(
            KnowledgeFileIngestionJob job,
            byte[] bytes,
            String title,
            String category,
            String source,
            List<String> tags,
            List<String> roles) {
        KnowledgeFileIngestionJob current = jobStore.update(KnowledgeIngestionJobMutations.withStatus(
                job,
                KnowledgeIngestionStatuses.PARSING,
                "Parsing knowledge file text",
                null,
                0));
        try {
            KnowledgeFileTextExtractionService.ExtractedKnowledgeText extracted =
                    textExtractionService.extract(bytes, job.fileName());
            current = jobStore.update(KnowledgeIngestionJobMutations.withStatus(
                    current,
                    KnowledgeIngestionStatuses.INDEXING,
                    "Indexing knowledge document and vector chunks",
                    null,
                    0));
            KnowledgeDocument document = new KnowledgeDocument(
                    "KB-FILE-" + job.sha256().substring(0, 12).toUpperCase(Locale.ROOT),
                    title,
                    extracted.text(),
                    category,
                    source,
                    tags,
                    normalizeRoles(roles),
                    job.createdBy(),
                    LocalDateTime.now());
            int chunkCount = knowledgeBaseService.saveDocument(document).size();
            jobStore.update(KnowledgeIngestionJobMutations.withStatus(
                    current,
                    KnowledgeIngestionStatuses.READY,
                    "Knowledge file indexed successfully",
                    document.documentId(),
                    chunkCount,
                    chunkCount,
                    null));
        } catch (RuntimeException ex) {
            jobStore.update(KnowledgeIngestionJobMutations.withStatus(
                    current,
                    KnowledgeIngestionStatuses.FAILED,
                    safeMessage(ex),
                    current.documentId(),
                    current.chunkCount(),
                    current.vectorCount(),
                    safeMessage(ex)));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        jobStore.markInterruptedJobsFailed();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private static List<String> splitCsv(String value, List<String> fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(value.split("[,;|\\n]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .forEach(values::add);
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    private static List<String> normalizeRoles(List<String> roles) {
        List<String> normalized = (roles == null ? List.<String>of() : roles).stream()
                .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("ADMIN") : normalized;
    }

    private static String titleFromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "knowledge.txt";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
