package com.aicampus.job.service.store;

import com.aicampus.common.dto.JobSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryJobRecordStore implements JobRecordStore {
    private final ConcurrentMap<String, JobSummary> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(JobSummary job) {
        jobs.put(job.jobId(), job);
    }

    @Override
    public Optional<JobSummary> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public List<JobSummary> listAll() {
        return sorted(jobs.values().stream().toList());
    }

    private static List<JobSummary> sorted(List<JobSummary> values) {
        return new ArrayList<>(values).stream()
                .sorted(Comparator.comparing(JobSummary::jobId))
                .toList();
    }
}
