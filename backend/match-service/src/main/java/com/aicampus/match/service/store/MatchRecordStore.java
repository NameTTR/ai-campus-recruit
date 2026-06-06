package com.aicampus.match.service.store;

import com.aicampus.common.dto.MatchResult;
import java.util.List;

public interface MatchRecordStore {
    void save(MatchResult match);

    List<MatchResult> listAll();

    List<MatchResult> listByStudent(String studentId);

    List<MatchResult> listByJob(String jobId);
}
