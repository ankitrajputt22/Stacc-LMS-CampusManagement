-- v0.8.3: moderation of course discussions by the course's faculty. Threads and replies can be hidden (kept, never
-- deleted, shown to students as hidden) and threads locked against new replies. Students report posts with a reason;
-- faculty resolve a post's open reports together, by hiding it or dismissing them. One open report per person and post.
ALTER TABLE lms_discussion_threads
    ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_at DATETIME(6) NULL,
    ADD COLUMN hidden_by_account_id BIGINT NULL,
    ADD CONSTRAINT fk_lms_discussion_threads_hidden_by FOREIGN KEY (hidden_by_account_id) REFERENCES user_accounts (id);

ALTER TABLE lms_discussion_replies
    ADD COLUMN hidden_at DATETIME(6) NULL,
    ADD COLUMN hidden_by_account_id BIGINT NULL,
    ADD CONSTRAINT fk_lms_discussion_replies_hidden_by FOREIGN KEY (hidden_by_account_id) REFERENCES user_accounts (id);

CREATE TABLE lms_discussion_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    reply_id BIGINT NULL,
    reporter_account_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    resolved_by_account_id BIGINT NULL,
    resolution VARCHAR(10) NULL,
    -- Set only while the report is open, so a person has at most one open report per post.
    open_key VARCHAR(70) AS (CASE WHEN resolved_at IS NULL
        THEN CONCAT(reporter_account_id, ':', thread_id, ':', COALESCE(reply_id, 0)) END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_discussion_reports_thread FOREIGN KEY (thread_id) REFERENCES lms_discussion_threads (id),
    CONSTRAINT fk_lms_discussion_reports_reply FOREIGN KEY (reply_id) REFERENCES lms_discussion_replies (id),
    CONSTRAINT fk_lms_discussion_reports_reporter FOREIGN KEY (reporter_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_lms_discussion_reports_resolver FOREIGN KEY (resolved_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_lms_discussion_reports_open UNIQUE (open_key),
    CONSTRAINT ck_lms_discussion_reports_resolution CHECK (resolution IS NULL OR resolution IN ('HIDDEN', 'DISMISSED')),
    CONSTRAINT ck_lms_discussion_reports_resolved CHECK ((resolved_at IS NULL) = (resolution IS NULL)),
    INDEX ix_lms_discussion_reports_thread (thread_id, resolved_at)
);

-- LMS_DISCUSSION_MODERATE: hiding and locking discussions and resolving reports in the courses one teaches. By name.
INSERT INTO permissions (code) VALUES ('LMS_DISCUSSION_MODERATE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'LMS_DISCUSSION_MODERATE' WHERE r.name = 'FACULTY';
