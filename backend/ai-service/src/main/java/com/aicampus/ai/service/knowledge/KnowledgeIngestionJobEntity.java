package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeFileIngestionJob;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("ai_knowledge_ingestion_job")
public class KnowledgeIngestionJobEntity {
    @TableId(value = "job_id", type = IdType.INPUT)
    private String jobId;

    @TableField("document_id")
    private String documentId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_format")
    private String fileFormat;

    @TableField("file_size")
    private long fileSize;

    @TableField("sha256")
    private String sha256;

    @TableField("title")
    private String title;

    @TableField("category")
    private String category;

    @TableField("source")
    private String source;

    @TableField("status")
    private String status;

    @TableField("message")
    private String message;

    @TableField("object_key")
    private String objectKey;

    @TableField("storage_provider")
    private String storageProvider;

    @TableField("storage_status")
    private String storageStatus;

    @TableField("chunk_count")
    private int chunkCount;

    @TableField("vector_count")
    private int vectorCount;

    @TableField("error")
    private String error;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public static KnowledgeIngestionJobEntity fromJob(KnowledgeFileIngestionJob job) {
        KnowledgeIngestionJobEntity entity = new KnowledgeIngestionJobEntity();
        entity.setJobId(job.jobId());
        entity.setDocumentId(job.documentId());
        entity.setFileName(job.fileName());
        entity.setFileFormat(job.fileFormat());
        entity.setFileSize(job.fileSize());
        entity.setSha256(job.sha256());
        entity.setTitle(job.title());
        entity.setCategory(job.category());
        entity.setSource(job.source());
        entity.setStatus(job.status());
        entity.setMessage(job.message());
        entity.setObjectKey(job.objectKey());
        entity.setStorageProvider(job.storageProvider());
        entity.setStorageStatus(job.storageStatus());
        entity.setChunkCount(job.chunkCount());
        entity.setVectorCount(job.vectorCount());
        entity.setError(job.error());
        entity.setCreatedBy(job.createdBy());
        entity.setCreatedAt(job.createdAt());
        entity.setUpdatedAt(job.updatedAt());
        return entity;
    }

    public KnowledgeFileIngestionJob toJob() {
        return new KnowledgeFileIngestionJob(
                jobId,
                documentId,
                fileName,
                fileFormat,
                fileSize,
                sha256,
                title,
                category,
                source,
                status,
                message,
                objectKey,
                storageProvider,
                storageStatus,
                chunkCount,
                vectorCount,
                error,
                createdBy,
                createdAt,
                updatedAt);
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getStorageStatus() {
        return storageStatus;
    }

    public void setStorageStatus(String storageStatus) {
        this.storageStatus = storageStatus;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public int getVectorCount() {
        return vectorCount;
    }

    public void setVectorCount(int vectorCount) {
        this.vectorCount = vectorCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
