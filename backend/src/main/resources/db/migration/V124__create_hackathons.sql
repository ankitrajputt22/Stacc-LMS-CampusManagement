-- v0.12.12: hackathons. The campus office runs them: teams of students (one team per hackathon each, within a size
-- limit), one submission per team replaced until the end, and faculty judges who score submissions after the end.
-- Scores stay hidden from participants until results are released; totals and places are worked out on read.
CREATE TABLE hackathons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    max_team_size INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    results_released_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_hackathons PRIMARY KEY (id),
    CONSTRAINT ck_hackathons_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RESULTS_RELEASED', 'ARCHIVED')),
    CONSTRAINT ck_hackathons_team_size CHECK (max_team_size BETWEEN 1 AND 10),
    CONSTRAINT ck_hackathons_times CHECK (ends_at > starts_at)
);

CREATE TABLE hackathon_teams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hackathon_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_hackathon_teams PRIMARY KEY (id),
    CONSTRAINT fk_hackathon_teams_hackathon FOREIGN KEY (hackathon_id) REFERENCES hackathons (id),
    CONSTRAINT uq_hackathon_teams_name UNIQUE (hackathon_id, name)
);

CREATE TABLE hackathon_team_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    hackathon_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN left_at IS NULL THEN CONCAT(hackathon_id, ':', account_id) END) STORED,
    CONSTRAINT pk_hackathon_team_members PRIMARY KEY (id),
    CONSTRAINT fk_hackathon_team_members_team FOREIGN KEY (team_id) REFERENCES hackathon_teams (id),
    CONSTRAINT fk_hackathon_team_members_hackathon FOREIGN KEY (hackathon_id) REFERENCES hackathons (id),
    CONSTRAINT fk_hackathon_team_members_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hackathon_team_members_live UNIQUE (live_key)
);

CREATE TABLE hackathon_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    link VARCHAR(500) NULL,
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_hackathon_submissions PRIMARY KEY (id),
    CONSTRAINT fk_hackathon_submissions_team FOREIGN KEY (team_id) REFERENCES hackathon_teams (id),
    CONSTRAINT uq_hackathon_submissions_team UNIQUE (team_id)
);

CREATE TABLE hackathon_judges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hackathon_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    CONSTRAINT pk_hackathon_judges PRIMARY KEY (id),
    CONSTRAINT fk_hackathon_judges_hackathon FOREIGN KEY (hackathon_id) REFERENCES hackathons (id),
    CONSTRAINT fk_hackathon_judges_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hackathon_judges UNIQUE (hackathon_id, account_id)
);

CREATE TABLE hackathon_scores (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    judge_account_id BIGINT NOT NULL,
    score INT NOT NULL,
    comment VARCHAR(1000) NULL,
    scored_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_hackathon_scores PRIMARY KEY (id),
    CONSTRAINT fk_hackathon_scores_submission FOREIGN KEY (submission_id) REFERENCES hackathon_submissions (id),
    CONSTRAINT fk_hackathon_scores_judge FOREIGN KEY (judge_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hackathon_scores UNIQUE (submission_id, judge_account_id),
    CONSTRAINT ck_hackathon_scores_score CHECK (score BETWEEN 0 AND 100)
);

INSERT INTO permissions (code) VALUES ('HACKATHON_PARTICIPATE'), ('HACKATHON_JUDGE'), ('HACKATHON_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HACKATHON_PARTICIPATE' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HACKATHON_JUDGE' WHERE r.name = 'FACULTY';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HACKATHON_MANAGE' WHERE r.name = 'ADMIN';
