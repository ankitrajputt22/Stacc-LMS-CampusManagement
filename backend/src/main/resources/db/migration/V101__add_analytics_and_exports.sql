-- v0.16.1, v0.16.2, v0.16.5, v0.16.6: analytics permissions, the report service's background exports, and indexes
-- found missing in the performance review. Analytics are worked out live; only export jobs and their files are kept,
-- and an export's file is removed once it expires. Percentages and averages are shown only for groups of at least
-- five students, and students are never ranked.
CREATE TABLE report_exports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    kind VARCHAR(40) NOT NULL,
    parameter_value BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    object_key VARCHAR(300) NULL,
    file_name VARCHAR(200) NULL,
    size_bytes BIGINT NULL,
    failure VARCHAR(300) NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_report_exports_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_report_exports_kind CHECK (kind IN ('ADMIN_ENROLLMENTS', 'COURSE_ANALYTICS')),
    CONSTRAINT ck_report_exports_status CHECK (status IN ('QUEUED', 'RUNNING', 'READY', 'FAILED', 'EXPIRED')),
    INDEX ix_report_exports_account (user_account_id, created_at),
    INDEX ix_report_exports_status (status, expires_at)
);

-- Performance review: lookups that run on every page load and had no index of their own.
CREATE INDEX ix_course_enrollments_offering_status ON course_enrollments (course_offering_id, status);
CREATE INDEX ix_attendance_sessions_offering_status ON attendance_sessions (course_offering_id, status);

-- ANALYTICS_VIEW_OWN: a student's own analytics. COURSE_ANALYTICS_VIEW: analytics of the courses a faculty member
-- teaches. REPORT_EXPORT: background exports of reports one may already read. Roles by name.
INSERT INTO permissions (code) VALUES ('ANALYTICS_VIEW_OWN'), ('COURSE_ANALYTICS_VIEW'), ('REPORT_EXPORT');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ANALYTICS_VIEW_OWN' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'COURSE_ANALYTICS_VIEW' WHERE r.name = 'FACULTY';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'REPORT_EXPORT' WHERE r.name IN ('FACULTY', 'ADMIN');
