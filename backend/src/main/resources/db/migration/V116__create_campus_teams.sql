-- v0.11.9: teams of sports groups. A team belongs to one sports group and one season; its roster holds active members
-- of that group as players, with at most one current captain and one current vice-captain (unique generated columns,
-- changed under the team row lock). Players leave the roster but are never deleted; teams are made inactive.
CREATE TABLE campus_teams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    season VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_campus_teams PRIMARY KEY (id),
    CONSTRAINT fk_campus_teams_group FOREIGN KEY (group_id) REFERENCES campus_groups (id),
    CONSTRAINT fk_campus_teams_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_campus_teams_name UNIQUE (group_id, name, season)
);

CREATE TABLE campus_team_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN left_at IS NULL THEN CONCAT(team_id, ':', account_id) END) STORED,
    leader_key VARCHAR(41) AS (CASE WHEN left_at IS NULL AND role IN ('CAPTAIN', 'VICE_CAPTAIN') THEN CONCAT(team_id, ':', role) END) STORED,
    CONSTRAINT pk_campus_team_members PRIMARY KEY (id),
    CONSTRAINT fk_campus_team_members_team FOREIGN KEY (team_id) REFERENCES campus_teams (id),
    CONSTRAINT fk_campus_team_members_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_campus_team_members_role CHECK (role IN ('PLAYER', 'CAPTAIN', 'VICE_CAPTAIN')),
    CONSTRAINT uq_campus_team_members_live UNIQUE (live_key),
    CONSTRAINT uq_campus_team_members_leader UNIQUE (leader_key),
    INDEX ix_campus_team_members_account (account_id)
);
