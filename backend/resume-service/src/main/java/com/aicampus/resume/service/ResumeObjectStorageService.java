package com.aicampus.resume.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ResumeObjectStorageService {
    private final java.util.concurrent.ConcurrentMap<String, byte[]> localObjects = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, String> localTypes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(ResumeObjectStorageService.class);
    private final boolean enabled;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final String publicEndpoint;
    private volatile MinioClient client;
    private volatile MinioClient publicClient;

    public ResumeObjectStorageService(boolean enabled, String endpoint, String accessKey, String secretKey, String bucket) {
        this(enabled, endpoint, accessKey, secretKey, bucket, endpoint);
    }

    @Autowired
    public ResumeObjectStorageService(
            @Value("${resume.storage.enabled:false}") boolean enabled,
            @Value("${resume.storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${resume.storage.access-key:minioadmin}") String accessKey,
            @Value("${resume.storage.secret-key:minioadmin}") String secretKey,
            @Value("${resume.storage.bucket:resumes}") String bucket,
            @Value("${resume.storage.public-endpoint:}") String publicEndpoint) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.publicEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
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
            try (InputStream stream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(
                                        stream, file.getSize(), -1L)
                                .contentType(contentType(file))
                                .build());
            }
            return new StoredResumeObject(objectKey, "minio", "STORED");
        } catch (Exception ex) {
            return new StoredResumeObject(objectKey, "minio", "FAILED");
        }
    }

    /**
     * Best-effort cleanup for a file that was uploaded before its database transaction completed,
     * or when a persisted resume is deleted.
     */
    public void storeBytes(String objectKey, byte[] bytes, String contentType) {
        if (!enabled) { localObjects.put(objectKey, bytes.clone()); localTypes.put(objectKey, contentType); return; }
        try { MinioClient c=minioClient(); ensureBucket(c); c.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(new java.io.ByteArrayInputStream(bytes),(long)bytes.length,-1L).contentType(contentType).build()); } catch(Exception ex) { throw new IllegalStateException("Unable to store object",ex); }
    }

    public byte[] readBytes(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        if (!enabled) return localObjects.get(objectKey);
        try (InputStream in = minioClient().getObject(io.minio.GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return in.readAllBytes();
        } catch (Exception ex) {
            log.warn("Unable to read object {}", objectKey, ex);
            return null;
        }
    }

    public String signedUrl(String objectKey) {
        if (!enabled) { byte[] b=localObjects.get(objectKey); String t=localTypes.getOrDefault(objectKey,"application/octet-stream"); return b==null ? "local-demo://" + objectKey : "data:" + t + ";base64," + java.util.Base64.getEncoder().encodeToString(b); }
        try { return signingClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Http.Method.GET).bucket(bucket).object(objectKey).expiry(15, TimeUnit.MINUTES).build()); } catch (Exception ex) { throw new IllegalStateException("Unable to create signed object URL", ex); }
    }

    public boolean delete(String objectKey) {
        if (!enabled || objectKey == null || objectKey.isBlank()) {
            return true;
        }
        try {
            minioClient()
                    .removeObject(
                            RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (Exception ex) {
            log.warn("Unable to delete resume object from MinIO, objectKey={}", objectKey, ex);
            return false;
        }
    }

    private MinioClient signingClient() {
        if (publicEndpoint.equals(endpoint)) return minioClient();
        MinioClient current = publicClient;
        if (current == null) {
            synchronized (this) {
                current = publicClient;
                if (current == null) {
                    current = MinioClient.builder().endpoint(publicEndpoint).credentials(accessKey, secretKey).region("us-east-1").build();
                    publicClient = current;
                }
            }
        }
        return current;
    }

    private MinioClient minioClient() {
        MinioClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current =
                            MinioClient.builder()
                                    .endpoint(endpoint)
                                    .credentials(accessKey, secretKey)
                                    .region("us-east-1")
                                    .build();
                    client = current;
                }
            }
        }
        return current;
    }

    private void ensureBucket(MinioClient minioClient) throws Exception {
        boolean exists =
                minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
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
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }

    public record StoredResumeObject(
            String objectKey, String storageProvider, String storageStatus) {}
}
