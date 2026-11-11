-- v0.12.11: innovation. Students and faculty submit ideas; faculty reviewers give each open idea one score (1 to 5)
-- and comment, never on their own; the campus office decides (shortlisted, then accepted or not selected) with a note.
-- Ideas are withdrawn, never deleted; reviews are kept.
CREATE TABLE innovation_ideas (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submitter_account_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    problem TEXT NOT NULL,
    solution TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    decision_note VARCHAR(1000) NULL,
    decided_at DATETIME(6) NULL,
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_innovation_ideas PRIMARY KEY (id),
    CONSTRAINT fk_innovation_ideas_submitter FOREIGN KEY (submitter_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_innovation_ideas_status CHECK (status IN ('SUBMITTED', 'SHORTLISTED', 'ACCEPTED', 'NOT_SELECTED', 'WITHDRAWN')),
    INDEX ix_innovation_ideas_submitter (submitter_account_id),
    INDEX ix_innovation_ideas_status (status)
);

CREATE TABLE innovation_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    idea_id BIGINT NOT NULL,
    reviewer_account_id BIGINT NOT NULL,
    score INT NOT NULL,
    comment TEXT NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_innovation_reviews PRIMARY KEY (id),
    CONSTRAINT fk_innovation_reviews_idea FOREIGN KEY (idea_id) REFERENCES innovation_ideas (id),
    CONSTRAINT fk_innovation_reviews_reviewer FOREIGN KEY (reviewer_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_innovation_reviews_one UNIQUE (idea_id, reviewer_account_id),
    CONSTRAINT ck_innovation_reviews_score CHECK (score BETWEEN 1 AND 5)
);

INSERT INTO permissions (code) VALUES ('INNOVATION_SUBMIT'), ('INNOVATION_REVIEW'), ('INNOVATION_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'INNOVATION_SUBMIT' WHERE r.name IN ('STUDENT', 'FACULTY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'INNOVATION_REVIEW' WHERE r.name = 'FACULTY';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'INNOVATION_MANAGE' WHERE r.name = 'ADMIN';
