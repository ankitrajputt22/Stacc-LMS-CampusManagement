-- v0.13.7: AI controls and limits.
-- A course's switch for the student AI Study Assistant, set by the faculty who teach it. No row means it is on.
CREATE TABLE ai_course_controls (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    student_ai_enabled BOOLEAN NOT NULL,
    updated_by_account_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_course_controls_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_course_controls_updated_by FOREIGN KEY (updated_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_ai_course_controls_lms_course UNIQUE (lms_course_id)
);

-- One row per AI request, without its content: the kind, the course, the outcome, and how long it took. Never the
-- question, the answer, the sources, or who asked. Rows older than the retention period are removed.
CREATE TABLE ai_request_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(30) NOT NULL,
    lms_course_id BIGINT NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    duration_ms INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_request_log_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT ck_ai_request_log_kind CHECK (kind IN ('ASK', 'SUMMARY', 'PRACTICE', 'ASSESSMENT_DRAFT')),
    CONSTRAINT ck_ai_request_log_outcome CHECK (outcome IN ('SUCCEEDED', 'INSUFFICIENT_CONTEXT', 'REFUSED', 'BUSY', 'FAILED')),
    INDEX ix_ai_request_log_created (created_at)
);
