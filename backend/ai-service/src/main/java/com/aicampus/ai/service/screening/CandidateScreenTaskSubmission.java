package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.CandidateScreenTask;

record CandidateScreenTaskSubmission(CandidateScreenTask task, CandidateScreenRequest request, boolean created) {
}
