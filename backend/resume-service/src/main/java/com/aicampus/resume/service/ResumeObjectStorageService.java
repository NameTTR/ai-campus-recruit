package com.aicampus.resume.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeObjectStorageService {
    private final boolean enabled;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private volatile MinioClient client;

    public ResumeObjectStorageService(
            @Value("${resume.storage.enabled:false}") boolean enabled,
            @Value("${resume.storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${resume.storage.access-key:minioadmin}") String accessKey,
            @Value("${resume.storage.secret-key:minioadmin}") String secretKey,
            @Value("${resume.storage.bucket:resumes}") String bucket) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    public StoredResumeObject store(String resumeId, MultipartFile file) {
        String safeName = sanitize(file.getOriginalFilename());
        String objectKey = "resumes/" + resumeId + "/" + safeName;
        if (!enabled) {
            return new StoredResumeObject(objectKey, "local-demo", "SKIPPED");
        }
        try {
            MinioClient minioClient = minioClient();
            ensureBucket(minioClient);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1L)
                    .contentType(contentType(file))
                    .build());
            return new StoredResumeObject(objectKey, "minio", "STORED");
        } catch (Exception ex) {
            return new StoredResumeObject(objectKey, "minio", "FAILED");
        }
    }

    private MinioClient minioClient() {
        MinioClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = MinioClient.builder()
                            .endpoint(endpoint)
                            .credentials(accessKey, secretKey)
                            .build();
                    client = current;
                }
            }
        }
        return current;
    }

    private void ensureBucket(MinioClient minioClient) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static String sanitize(String fileName) {
        String resolved = fileName == null || fileName.isBlank() ? "resume.pdf" : fileName;
        return resolved.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String contentType(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    public record StoredResumeObject(String objectKey, String storageProvider, String storageStatus) {
    }
}
