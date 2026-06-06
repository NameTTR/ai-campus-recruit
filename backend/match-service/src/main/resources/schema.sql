CREATE TABLE IF NOT EXISTS match_result_record (
    match_id VARCHAR(64) NOT NULL PRIMARY KEY,
    resume_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    score INT NOT NULL,
    strengths TEXT NOT NULL,
    gaps TEXT NOT NULL,
    suggestions TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_match_result_record_student_created (student_id, created_at),
    KEY idx_match_result_record_job_created (job_id, created_at),
    KEY idx_match_result_record_resume_created (resume_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
