package com.aicampus.ai.service.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "ai_knowledge_chunk", autoResultMap = true)
public class KnowledgeChunkEntity {
    @TableId(value = "chunk_id", type = IdType.INPUT)
    private String chunkId;

    @TableField("document_id")
    private String documentId;

    @TableField("chunk_index")
    private int chunkIndex;

    @TableField("title")
    private String title;

    @TableField("chunk_text")
    private String text;

    @TableField("category")
    private String category;

    @TableField("source")
    private String source;

    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(value = "roles", typeHandler = JacksonTypeHandler.class)
    private List<String> roles;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField(value = "embedding", typeHandler = JacksonTypeHandler.class)
    private List<Double> embedding;

    public static KnowledgeChunkEntity fromRecord(KnowledgeChunkRecord record) {
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setChunkId(record.chunkId());
        entity.setDocumentId(record.documentId());
        entity.setChunkIndex(record.chunkIndex());
        entity.setTitle(record.title());
        entity.setText(record.text());
        entity.setCategory(record.category());
        entity.setSource(record.source());
        entity.setTags(record.tags());
        entity.setRoles(record.roles());
        entity.setCreatedBy(record.createdBy());
        entity.setCreatedAt(record.createdAt());
        entity.setEmbedding(record.embedding());
        return entity;
    }

    public KnowledgeChunkRecord toRecord() {
        return new KnowledgeChunkRecord(
                chunkId,
                documentId,
                chunkIndex,
                title,
                text,
                category,
                source,
                tags == null ? List.of() : tags,
                roles == null ? List.of() : roles,
                createdBy,
                createdAt,
                embedding == null ? List.of() : embedding);
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }
}
