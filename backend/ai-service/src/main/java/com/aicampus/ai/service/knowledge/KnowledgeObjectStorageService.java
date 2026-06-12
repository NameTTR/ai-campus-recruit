package com.aicampus.ai.service.knowledge;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
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
}
