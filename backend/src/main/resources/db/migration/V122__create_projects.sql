-- v0.12.10: projects. One team model: a student creates a project and leads it; other students ask to join and the lead
-- accepts them within the project's member limit (under the project row lock); the lead invites faculty mentors, who
-- accept or decline. Memberships and mentorships keep their history; projects are closed or archived, never deleted.
CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    lead_account_id BIGINT NOT NULL,
    max_members INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_lead FOREIGN KEY (lead_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_projects_status CHECK (status IN ('OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT ck_projects_members CHECK (max_members BETWEEN 1 AND 20)
);

CREATE TABLE project_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(500) NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    left_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN status IN ('REQUESTED', 'ACTIVE') THEN CONCAT(project_id, ':', account_id) END) STORED,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_project_members_status CHECK (status IN ('REQUESTED', 'ACTIVE', 'DECLINED', 'WITHDRAWN', 'LEFT')),
    CONSTRAINT uq_project_members_live UNIQUE (live_key),
    INDEX ix_project_members_account (account_id)
);

CREATE TABLE project_mentors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    invited_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN status IN ('INVITED', 'ACCEPTED') THEN CONCAT(project_id, ':', account_id) END) STORED,
    CONSTRAINT pk_project_mentors PRIMARY KEY (id),
    CONSTRAINT fk_project_mentors_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_mentors_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_project_mentors_status CHECK (status IN ('INVITED', 'ACCEPTED', 'DECLINED', 'ENDED')),
    CONSTRAINT uq_project_mentors_live UNIQUE (live_key),
    INDEX ix_project_mentors_account (account_id)
);

INSERT INTO permissions (code) VALUES ('PROJECT_USE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'PROJECT_USE' WHERE r.name IN ('STUDENT', 'FACULTY');
