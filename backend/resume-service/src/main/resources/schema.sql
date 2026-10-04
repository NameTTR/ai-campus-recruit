CREATE TABLE IF NOT EXISTS resume_summary_record (
    resume_id VARCHAR(64) NOT NULL PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    education VARCHAR(255) NOT NULL,
    skills TEXT NOT NULL,
    projects TEXT NOT NULL,
    diagnosis TEXT NOT NULL,
    score INT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    storage_provider VARCHAR(64) NOT NULL,
    storage_status VARCHAR(64) NOT NULL,
    source_format VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    parse_status VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    parsed_text_length INT NOT NULL DEFAULT 0,
    parsed_text MEDIUMTEXT NOT NULL,
    diagnosis_history LONGTEXT NOT NULL,
    structured_diagnosis LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_resume_summary_record_student_updated (student_id, updated_at),
    KEY idx_resume_summary_record_parse_status_updated (parse_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_workspace_profile (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    profile_json LONGTEXT NOT NULL,
    source_resume_id VARCHAR(64) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_workspace_draft (
    draft_id VARCHAR(64) NOT NULL PRIMARY KEY,
    resume_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL,
    profile_revision BIGINT NOT NULL,
    profile_snapshot_json LONGTEXT NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    target_role VARCHAR(128) NULL,
    job_snapshot_json LONGTEXT NULL,
    input_fingerprint VARCHAR(128) NOT NULL,
    data_json LONGTEXT NOT NULL,
    confirmed TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_resume_workspace_draft_fingerprint (user_id, input_fingerprint),
    KEY idx_resume_workspace_draft_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_workspace_draft_revision (
    draft_id VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    data_json LONGTEXT NOT NULL,
    confirmed TINYINT(1) NOT NULL DEFAULT 0,
    reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (draft_id, revision),
    CONSTRAINT fk_resume_workspace_revision_draft FOREIGN KEY (draft_id) REFERENCES resume_workspace_draft(draft_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_workspace_photo (
    object_key VARCHAR(512) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_resume_workspace_photo_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_workspace_export (
    export_id VARCHAR(64) NOT NULL PRIMARY KEY,
    draft_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    draft_revision BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    layout_issues_json TEXT NULL,
    page_count INT NOT NULL DEFAULT 0,
    docx_json TEXT NULL,
    pdf_json TEXT NULL,
    error_message VARCHAR(1000) NULL,
    draft_snapshot_json LONGTEXT NOT NULL,
    docx_key VARCHAR(512) NULL,
    pdf_key VARCHAR(512) NULL,
    render_version VARCHAR(64) NOT NULL DEFAULT 'legacy',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_resume_workspace_export_revision_render (draft_id, draft_revision, render_version),
    KEY idx_resume_workspace_export_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
