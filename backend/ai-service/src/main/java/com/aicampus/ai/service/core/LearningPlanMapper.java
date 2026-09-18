package com.aicampus.ai.service.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LearningPlanMapper extends BaseMapper<LearningPlanEntity> {
    @Update("""
            UPDATE ai_learning_plan
            SET status = #{plan.status}, plan_snapshot = #{plan.planSnapshot}, updated_at = #{plan.updatedAt}
            WHERE plan_id = #{plan.planId} AND status = 'ACTIVE' AND plan_snapshot = #{expectedPlanSnapshot}
            """)
    int updateIfCurrentActive(
            @Param("plan") LearningPlanEntity plan,
            @Param("expectedPlanSnapshot") String expectedPlanSnapshot);

    @Update("""
            UPDATE ai_learning_plan
            SET status = #{plan.status}, plan_snapshot = #{plan.planSnapshot}, updated_at = #{plan.updatedAt}
            WHERE plan_id = #{plan.planId} AND version = #{expectedVersion} AND status = 'ACTIVE'
              AND plan_snapshot = #{expectedPlanSnapshot}
            """)
    int supersedeIfCurrentActive(
            @Param("plan") LearningPlanEntity plan,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedPlanSnapshot") String expectedPlanSnapshot);
}
