CREATE TABLE IF NOT EXISTS delivery_record (
    delivery_id VARCHAR(64) NOT NULL PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL,
    resume_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    resume_source_format VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    resume_parse_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    resume_parsed_text_length INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_delivery_record_student_created (student_id, created_at),
    KEY idx_delivery_record_company_created (company_id, created_at),
    KEY idx_delivery_record_company_status_created (company_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
