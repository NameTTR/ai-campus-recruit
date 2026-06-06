package com.aicampus.job.service.store;

import com.aicampus.common.dto.JobSummary;
import java.util.List;
import java.util.Optional;

public interface JobRecordStore {
    void save(JobSummary job);

    Optional<JobSummary> findById(String jobId);

    List<JobSummary> listAll();
}
