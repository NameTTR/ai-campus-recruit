package com.aicampus.ai.service.core;

import com.aicampus.common.dto.LearningPlan;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

@TableName("ai_learning_plan")
public class LearningPlanEntity {
    @TableId(value = "plan_id", type = IdType.INPUT)
    private String planId;
    @TableField("root_plan_id")
    private String rootPlanId;
    @TableField("student_id")
    private String studentId;
    @TableField("revision_of_plan_id")
    private String revisionOfPlanId;
    @TableField("version")
    private int version;
    @TableField("status")
    private String status;
    @TableField("plan_snapshot")
    private String planSnapshot;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public static LearningPlanEntity fromPlan(LearningPlan plan, ObjectMapper objectMapper)
            throws JsonProcessingException {
        LearningPlanEntity entity = new LearningPlanEntity();
        entity.setPlanId(plan.planId());
        entity.setRootPlanId(plan.rootPlanId());
        entity.setStudentId(plan.studentId());
        entity.setRevisionOfPlanId(plan.revisionOfPlanId());
        entity.setVersion(plan.version());
        entity.setStatus(plan.status());
        entity.setPlanSnapshot(objectMapper.writeValueAsString(plan));
        entity.setCreatedAt(plan.createdAt());
        entity.setUpdatedAt(plan.updatedAt());
        return entity;
    }

    public LearningPlan toPlan(ObjectMapper objectMapper) throws JsonProcessingException {
        return objectMapper.readValue(planSnapshot, LearningPlan.class);
    }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getRootPlanId() { return rootPlanId; }
    public void setRootPlanId(String rootPlanId) { this.rootPlanId = rootPlanId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getRevisionOfPlanId() { return revisionOfPlanId; }
    public void setRevisionOfPlanId(String revisionOfPlanId) { this.revisionOfPlanId = revisionOfPlanId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlanSnapshot() { return planSnapshot; }
    public void setPlanSnapshot(String planSnapshot) { this.planSnapshot = planSnapshot; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
