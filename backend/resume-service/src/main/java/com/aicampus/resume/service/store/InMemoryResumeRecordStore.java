package com.aicampus.resume.service.store;

import java.util.Comparator;
import java.util.List;
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

    @Override
    public List<ResumeRecord> listAll() {
        return resumes.values().stream()
                .sorted(Comparator.comparing(record -> record.summary().resumeId()))
                .toList();
    }
}
