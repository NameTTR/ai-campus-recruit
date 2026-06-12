package com.aicampus.ai.service.knowledge;

import com.aicampus.common.dto.KnowledgeDocument;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "ai_knowledge_document", autoResultMap = true)
public class KnowledgeDocumentEntity {
    @TableId(value = "document_id", type = IdType.INPUT)
    private String documentId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

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

    public static KnowledgeDocumentEntity fromDocument(KnowledgeDocument document) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setDocumentId(document.documentId());
        entity.setTitle(document.title());
        entity.setContent(document.content());
        entity.setCategory(document.category());
        entity.setSource(document.source());
        entity.setTags(document.tags());
        entity.setRoles(document.roles());
        entity.setCreatedBy(document.createdBy());
        entity.setCreatedAt(document.createdAt());
        return entity;
    }

    public KnowledgeDocument toDocument() {
        return new KnowledgeDocument(
                documentId,
                title,
                content,
                category,
                source,
                tags == null ? List.of() : tags,
                roles == null ? List.of() : roles,
                createdBy,
                createdAt);
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}
