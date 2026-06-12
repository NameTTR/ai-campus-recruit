package com.aicampus.ai.service.planning;

import com.aicampus.common.dto.AiPlanningRecord;
import java.util.List;

public interface AiPlanningRecordStore {
    void save(AiPlanningRecord record);

    List<AiPlanningRecord> listByStudent(String studentId, int limit);

    List<AiPlanningRecord> listAll(int limit);
}
