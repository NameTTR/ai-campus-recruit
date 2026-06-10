package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenTask;
import java.util.List;

interface CandidateScreenTaskStore {
    CandidateScreenTaskSubmission create(CandidateScreenTask task, CandidateScreenRequest request, String dedupKey);

    void update(CandidateScreenTask task);

    CandidateScreenTaskSnapshot get(String taskId, String companyId);

    List<CandidateScreenTask> list(String companyId, String deliveryId);

    default void markInterruptedTasksFailed() {
    }
}
