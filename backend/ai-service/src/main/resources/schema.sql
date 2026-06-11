CREATE TABLE IF NOT EXISTS ai_candidate_screen_record (
    screening_id VARCHAR(64) NOT NULL PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    delivery_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    resume_source_format VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    resume_parse_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    resume_parsed_text_length INT NOT NULL DEFAULT 0,
    score INT NOT NULL,
    recommendation TEXT NOT NULL,
    strengths TEXT NOT NULL,
    risks TEXT NOT NULL,
    interview_questions TEXT NOT NULL,
    next_actions TEXT NOT NULL,
    mocked TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_ai_candidate_screen_record_company_delivery_created (company_id, delivery_id, created_at),
    KEY idx_ai_candidate_screen_record_delivery_created (delivery_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_candidate_screen_task (
    task_id VARCHAR(64) NOT NULL PRIMARY KEY,
    delivery_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    resume_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    result_snapshot MEDIUMTEXT NULL,
    request_snapshot MEDIUMTEXT NOT NULL,
    dedup_key VARCHAR(160) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_ai_candidate_screen_task_dedup (dedup_key),
    KEY idx_ai_candidate_screen_task_company_delivery_created (company_id, delivery_id, created_at),
    KEY idx_ai_candidate_screen_task_status_updated (status, updated_at),
    KEY idx_ai_candidate_screen_task_source_delivery (source, delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_planning_record (
    record_id VARCHAR(64) NOT NULL PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    resume_id VARCHAR(64) NULL,
    target_role VARCHAR(128) NOT NULL,
    response_snapshot MEDIUMTEXT NOT NULL,
    mocked TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_ai_planning_record_student_created (student_id, created_at),
    KEY idx_ai_planning_record_operation_created (operation, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
