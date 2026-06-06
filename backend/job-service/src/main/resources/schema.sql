CREATE TABLE IF NOT EXISTS job_record (
    job_id VARCHAR(64) NOT NULL PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    company_name VARCHAR(128) NOT NULL,
    title VARCHAR(128) NOT NULL,
    city VARCHAR(64) NOT NULL,
    salary_range VARCHAR(64) NOT NULL,
    required_skills TEXT NOT NULL,
    description TEXT NOT NULL,
    ai_summary TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_job_record_company_updated (company_id, updated_at),
    KEY idx_job_record_city_updated (city, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
