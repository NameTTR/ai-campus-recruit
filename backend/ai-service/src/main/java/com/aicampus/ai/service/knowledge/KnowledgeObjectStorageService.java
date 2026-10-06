package com.aicampus.ai.service.knowledge;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeObjectStorageService {
    private final KnowledgeBaseProperties properties;
    private volatile MinioClient client;

    public KnowledgeObjectStorageService(KnowledgeBaseProperties properties) {
        this.properties = properties;
    }

    public StoredKnowledgeObject store(String jobId, String fileName, String contentType, byte[] bytes) {
        String objectKey = "knowledge/" + safeName(jobId) + "/" + safeName(fileName);
        KnowledgeBaseProperties.Storage storage = properties.getStorage();
        if (!storage.isEnabled()) {
            return new StoredKnowledgeObject(objectKey, "local-demo", "SKIPPED");
        }
        try {
            MinioClient minioClient = minioClient(storage);
            ensureBucket(minioClient, storage.getBucket());
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(storage.getBucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L)
                    .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                    .build());
            return new StoredKnowledgeObject(objectKey, "minio", "STORED");
        } catch (Exception ex) {
            return new StoredKnowledgeObject(objectKey, "minio", "FAILED:" + Instant.now());
        }
    }

    /** Caller must verify the current document permission before opening an original. */
    public OriginalKnowledgeObject read(String objectKey, Long offset, Long length) {
        if (objectKey == null || !objectKey.startsWith("knowledge/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("Invalid knowledge original object key");
        }
        KnowledgeBaseProperties.Storage storage = properties.getStorage();
        if (!storage.isEnabled()) throw new IllegalStateException("The original file is unavailable; extracted text remains readable");
        try {
            MinioClient minioClient = minioClient(storage);
            var stat = minioClient.statObject(StatObjectArgs.builder().bucket(storage.getBucket()).object(objectKey).build());
            long start = offset == null ? 0 : offset;
            long count = length == null ? stat.size() - start : length;
            if (start < 0 || start >= stat.size() || count <= 0 || count > stat.size() - start) {
                throw new IllegalArgumentException("Requested file range is unavailable");
            }
            GetObjectArgs.Builder request = GetObjectArgs.builder().bucket(storage.getBucket()).object(objectKey);
            if (offset != null || length != null) request.offset(start).length(count);
            InputStream stream = minioClient.getObject(request.build());
            return new OriginalKnowledgeObject(stream, stat.size(), start, count, stat.contentType());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("The original file could not be read; extracted text remains readable", ex);
        }
    }

    public long originalSize(String objectKey) {
        if (objectKey == null || !objectKey.startsWith("knowledge/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("Invalid knowledge original object key");
        }
        KnowledgeBaseProperties.Storage storage = properties.getStorage();
        if (!storage.isEnabled()) throw new IllegalStateException("The original file is unavailable");
        try {
            return minioClient(storage).statObject(StatObjectArgs.builder().bucket(storage.getBucket()).object(objectKey).build()).size();
        } catch (Exception ex) {
            throw new IllegalStateException("The original file is unavailable", ex);
        }
    }

    private MinioClient minioClient(KnowledgeBaseProperties.Storage storage) {
        MinioClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = MinioClient.builder()
                            .endpoint(storage.getEndpoint())
                            .credentials(storage.getAccessKey(), storage.getSecretKey())
                            .build();
                    client = current;
                }
            }
        }
        return current;
    }

    private void ensureBucket(MinioClient minioClient, String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static String safeName(String value) {
        String safe = value == null || value.isBlank() ? "knowledge-file" : value.trim();
        return safe.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record StoredKnowledgeObject(String objectKey, String storageProvider, String storageStatus) {
    }

    public record OriginalKnowledgeObject(InputStream stream, long totalBytes, long offset, long length, String contentType) {
    }
}
