-- v0.12.4: mentoring. Admins pair a faculty mentor with a student; a pair is ended, never deleted. The mentor records
-- meetings with shared notes (both read them) and private notes (the mentor only). Action items belong to the mentor or
-- the student, appear on the owner's To-Do list while open, and are completed once.
CREATE TABLE mentor_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mentor_account_id BIGINT NOT NULL,
    mentee_account_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN ended_at IS NULL THEN CONCAT(mentor_account_id, ':', mentee_account_id) END) STORED,
    CONSTRAINT pk_mentor_assignments PRIMARY KEY (id),
    CONSTRAINT fk_mentor_assignments_mentor FOREIGN KEY (mentor_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_mentor_assignments_mentee FOREIGN KEY (mentee_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_mentor_assignments_live UNIQUE (live_key),
    CONSTRAINT ck_mentor_assignments_two_people CHECK (mentor_account_id <> mentee_account_id),
    INDEX ix_mentor_assignments_mentee (mentee_account_id)
);

CREATE TABLE mentoring_meetings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    held_at DATETIME(6) NOT NULL,
    title VARCHAR(200) NOT NULL,
    shared_notes TEXT NULL,
    private_notes TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_mentoring_meetings PRIMARY KEY (id),
    CONSTRAINT fk_mentoring_meetings_assignment FOREIGN KEY (assignment_id) REFERENCES mentor_assignments (id),
    INDEX ix_mentoring_meetings_assignment (assignment_id, held_at)
);

CREATE TABLE mentoring_action_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL,
    owner VARCHAR(10) NOT NULL,
    due_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_mentoring_action_items PRIMARY KEY (id),
    CONSTRAINT fk_mentoring_action_items_meeting FOREIGN KEY (meeting_id) REFERENCES mentoring_meetings (id),
    CONSTRAINT ck_mentoring_action_items_owner CHECK (owner IN ('MENTOR', 'MENTEE'))
);

INSERT INTO permissions (code) VALUES ('MENTORING_USE'), ('MENTORING_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'MENTORING_USE' WHERE r.name IN ('STUDENT', 'FACULTY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'MENTORING_MANAGE' WHERE r.name = 'ADMIN';
