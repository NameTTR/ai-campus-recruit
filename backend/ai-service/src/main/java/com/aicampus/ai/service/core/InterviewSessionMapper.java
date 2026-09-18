package com.aicampus.ai.service.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionEntity> {
    @Update("""
            UPDATE ai_interview_session
            SET status = #{session.status}, session_snapshot = #{session.sessionSnapshot}, updated_at = #{session.updatedAt}
            WHERE session_id = #{session.sessionId} AND status = 'IN_PROGRESS'
              AND session_snapshot = #{expectedSessionSnapshot}
            """)
    int updateIfCurrentInProgress(
            @Param("session") InterviewSessionEntity session,
            @Param("expectedSessionSnapshot") String expectedSessionSnapshot);
}
