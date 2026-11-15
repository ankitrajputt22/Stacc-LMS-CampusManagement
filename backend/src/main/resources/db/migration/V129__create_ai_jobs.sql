-- v0.13.1: AI jobs. A course's AI knowledge refresh runs as a database-backed background job instead of inside the
-- request: one queued or running refresh per course (a unique generated column), claimed by a worker with
-- FOR UPDATE SKIP LOCKED, and finished with a short safe result or error. Jobs are kept as history.
CREATE TABLE ai_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(30) NOT NULL,
    lms_course_id BIGINT NOT NULL,
    requested_by_account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    result_summary VARCHAR(200) NULL,
    error_message VARCHAR(300) NULL,
    live_key VARCHAR(60) AS (CASE WHEN status IN ('QUEUED', 'RUNNING') THEN CONCAT(kind, ':', lms_course_id) END) STORED,
    CONSTRAINT pk_ai_jobs PRIMARY KEY (id),
    CONSTRAINT fk_ai_jobs_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_jobs_requested_by FOREIGN KEY (requested_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_ai_jobs_kind CHECK (kind IN ('REFRESH_INDEX')),
    CONSTRAINT ck_ai_jobs_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT uq_ai_jobs_live UNIQUE (live_key),
    INDEX ix_ai_jobs_status (status, id),
    INDEX ix_ai_jobs_course (lms_course_id, id)
);
