package com.aicampus.resume.service.store;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryResumeRecordStore implements ResumeRecordStore {
    private final ConcurrentMap<String, ResumeRecord> resumes = new ConcurrentHashMap<>();

    @Override
    public void save(ResumeRecord record) {
        resumes.put(record.summary().resumeId(), record);
    }

    @Override
    public Optional<ResumeRecord> findById(String resumeId) {
        return Optional.ofNullable(resumes.get(resumeId));
    }
}
