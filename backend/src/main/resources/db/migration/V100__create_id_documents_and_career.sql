-- v0.12.1, v0.12.2, and v0.12.6 to v0.12.8: digital ID verification, the document vault with certificate requests and a
-- public authenticity check, career profiles with resume versions, and one opportunities engine (placements,
-- internships, research openings, scholarships) with stages, applications, offers, and a placement policy. Files live
-- in private storage; only fingerprints of verification codes are stored. Nothing here is deleted.
CREATE TABLE vault_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_account_id BIGINT NOT NULL,
    origin VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    object_key VARCHAR(300) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    issued_by_user_account_id BIGINT NULL,
    verification_fingerprint CHAR(64) NULL,
    certificate_request_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    withdrawn_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vault_documents_key UNIQUE (object_key),
    CONSTRAINT uk_vault_documents_verification UNIQUE (verification_fingerprint),
    CONSTRAINT fk_vault_documents_owner FOREIGN KEY (owner_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_vault_documents_issuer FOREIGN KEY (issued_by_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_vault_documents_origin CHECK (origin IN ('ISSUED', 'UPLOADED')),
    INDEX ix_vault_documents_owner (owner_user_account_id, created_at)
);

CREATE TABLE certificate_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_user_account_id BIGINT NOT NULL,
    certificate_type VARCHAR(30) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    decided_by_user_account_id BIGINT NULL,
    decision_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_certificate_requests_requester FOREIGN KEY (requester_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_certificate_requests_decider FOREIGN KEY (decided_by_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_certificate_requests_type CHECK (certificate_type IN ('BONAFIDE', 'TRANSCRIPT', 'CHARACTER', 'COURSE_COMPLETION', 'OTHER')),
    CONSTRAINT ck_certificate_requests_status CHECK (status IN ('REQUESTED', 'ISSUED', 'REFUSED', 'CANCELLED')),
    INDEX ix_certificate_requests_status (status, created_at)
);

ALTER TABLE vault_documents
    ADD CONSTRAINT fk_vault_documents_request FOREIGN KEY (certificate_request_id) REFERENCES certificate_requests (id);

CREATE TABLE career_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    headline VARCHAR(200) NULL,
    skills VARCHAR(1000) NULL,
    links VARCHAR(1500) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_career_profiles_account UNIQUE (user_account_id),
    CONSTRAINT fk_career_profiles_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

CREATE TABLE career_resumes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    label VARCHAR(100) NOT NULL,
    object_key VARCHAR(300) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_career_resumes_key UNIQUE (object_key),
    CONSTRAINT fk_career_resumes_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

CREATE TABLE career_companies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    website VARCHAR(300) NULL,
    description VARCHAR(2000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_career_companies_name UNIQUE (name)
);

CREATE TABLE career_opportunities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    company_id BIGINT NULL,
    organization VARCHAR(200) NULL,
    description TEXT NULL,
    eligible_program_ids VARCHAR(500) NULL,
    deadline DATETIME(6) NOT NULL,
    stages VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_user_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_career_opportunities_company FOREIGN KEY (company_id) REFERENCES career_companies (id),
    CONSTRAINT fk_career_opportunities_creator FOREIGN KEY (created_by_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_career_opportunities_kind CHECK (kind IN ('PLACEMENT', 'INTERNSHIP', 'RESEARCH', 'SCHOLARSHIP')),
    CONSTRAINT ck_career_opportunities_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    INDEX ix_career_opportunities_status (status, deadline)
);

CREATE TABLE career_rounds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    opportunity_id BIGINT NOT NULL,
    stage_index INT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    venue VARCHAR(200) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_career_rounds_opportunity FOREIGN KEY (opportunity_id) REFERENCES career_opportunities (id),
    CONSTRAINT uk_career_rounds_stage UNIQUE (opportunity_id, stage_index)
);

CREATE TABLE career_applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    opportunity_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    resume_id BIGINT NULL,
    cover_note VARCHAR(2000) NULL,
    share_consent BOOLEAN NOT NULL,
    stage_index INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_career_applications UNIQUE (opportunity_id, user_account_id),
    CONSTRAINT fk_career_applications_opportunity FOREIGN KEY (opportunity_id) REFERENCES career_opportunities (id),
    CONSTRAINT fk_career_applications_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_career_applications_resume FOREIGN KEY (resume_id) REFERENCES career_resumes (id),
    CONSTRAINT ck_career_applications_status CHECK (status IN ('APPLIED', 'IN_PROGRESS', 'SELECTED', 'REJECTED', 'WITHDRAWN')),
    INDEX ix_career_applications_account (user_account_id, applied_at)
);

CREATE TABLE career_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    role_title VARCHAR(200) NOT NULL,
    compensation VARCHAR(200) NULL,
    respond_by DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_career_offers_application UNIQUE (application_id),
    CONSTRAINT fk_career_offers_application FOREIGN KEY (application_id) REFERENCES career_applications (id),
    CONSTRAINT ck_career_offers_status CHECK (status IN ('MADE', 'ACCEPTED', 'DECLINED'))
);

CREATE TABLE career_policy (
    id BIGINT NOT NULL,
    one_accepted_placement BOOLEAN NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_career_policy_single CHECK (id = 1)
);
INSERT INTO career_policy (id, one_accepted_placement, updated_at) VALUES (1, TRUE, UTC_TIMESTAMP(6));

-- ID_CARD_VERIFY: staff check a digital ID code. DOCUMENT_VAULT_USE: one's own vault and certificate requests.
-- DOCUMENT_ISSUE: the college issues certificates. CAREER_USE: a student's career profile and applications.
-- CAREER_MANAGE: companies, opportunities, applicants, offers, and the policy. Roles by name.
INSERT INTO permissions (code) VALUES ('ID_CARD_VERIFY'), ('DOCUMENT_VAULT_USE'), ('DOCUMENT_ISSUE'), ('CAREER_USE'), ('CAREER_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ID_CARD_VERIFY' WHERE r.name IN ('ADMIN', 'SECURITY', 'LIBRARIAN', 'WARDEN');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'DOCUMENT_VAULT_USE' WHERE r.name IN ('STUDENT', 'FACULTY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'DOCUMENT_ISSUE' WHERE r.name = 'ADMIN';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'CAREER_USE' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'CAREER_MANAGE' WHERE r.name IN ('ADMIN', 'PLACEMENT_OFFICER');
