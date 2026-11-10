-- v0.12.3: grievances. Categories each have a committee of named accounts and a response time in days. A case is
-- confidential: only its complainant and the active committee members of its category open it; admins manage
-- categories and committees but never read cases. Cases move forward only and are never deleted; overdue is worked out
-- from the respond-by times on read.
CREATE TABLE grievance_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    response_days INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_grievance_categories PRIMARY KEY (id),
    CONSTRAINT uq_grievance_categories_code UNIQUE (code),
    CONSTRAINT ck_grievance_categories_days CHECK (response_days BETWEEN 1 AND 90)
);

CREATE TABLE grievance_committee_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    added_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN ended_at IS NULL THEN CONCAT(category_id, ':', account_id) END) STORED,
    CONSTRAINT pk_grievance_committee_members PRIMARY KEY (id),
    CONSTRAINT fk_grievance_committee_members_category FOREIGN KEY (category_id) REFERENCES grievance_categories (id),
    CONSTRAINT fk_grievance_committee_members_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_grievance_committee_members_live UNIQUE (live_key),
    INDEX ix_grievance_committee_members_account (account_id)
);

CREATE TABLE grievance_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_number VARCHAR(20) NULL,
    category_id BIGINT NOT NULL,
    complainant_account_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    details TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    respond_by DATETIME(6) NOT NULL,
    review_started_at DATETIME(6) NULL,
    resolution TEXT NULL,
    resolved_at DATETIME(6) NULL,
    appeal_reason TEXT NULL,
    appealed_at DATETIME(6) NULL,
    appeal_respond_by DATETIME(6) NULL,
    appeal_decision TEXT NULL,
    closed_at DATETIME(6) NULL,
    CONSTRAINT pk_grievance_cases PRIMARY KEY (id),
    CONSTRAINT uq_grievance_cases_number UNIQUE (case_number),
    CONSTRAINT fk_grievance_cases_category FOREIGN KEY (category_id) REFERENCES grievance_categories (id),
    CONSTRAINT fk_grievance_cases_complainant FOREIGN KEY (complainant_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_grievance_cases_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'RESOLVED', 'APPEALED', 'CLOSED')),
    INDEX ix_grievance_cases_complainant (complainant_account_id),
    INDEX ix_grievance_cases_category (category_id, status)
);

INSERT INTO permissions (code) VALUES ('GRIEVANCE_SUBMIT'), ('GRIEVANCE_COMMITTEE_WORK'), ('GRIEVANCE_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'GRIEVANCE_SUBMIT' WHERE r.name IN ('STUDENT', 'FACULTY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'GRIEVANCE_COMMITTEE_WORK' WHERE r.name IN ('FACULTY', 'ADMIN');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'GRIEVANCE_MANAGE' WHERE r.name = 'ADMIN';
