package com.aicampus.resume.service.store;

import java.util.Optional;

public interface ResumeRecordStore {
    void save(ResumeRecord record);

    Optional<ResumeRecord> findById(String resumeId);
}
