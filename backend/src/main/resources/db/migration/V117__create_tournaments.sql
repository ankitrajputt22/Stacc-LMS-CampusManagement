-- v0.11.10: tournaments. The campus office runs tournaments with fixtures between our teams (campus_teams) or external
-- opponents named in text. A played fixture keeps text scores and a result (home win, away win, or draw); standings are
-- worked out from played fixtures on read, never stored. Tournaments are archived and fixtures cancelled, never deleted.
CREATE TABLE campus_tournaments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    sport VARCHAR(100) NOT NULL,
    season VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    CONSTRAINT pk_campus_tournaments PRIMARY KEY (id),
    CONSTRAINT fk_campus_tournaments_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_campus_tournaments_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE campus_fixtures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tournament_id BIGINT NOT NULL,
    home_team_id BIGINT NULL,
    home_name VARCHAR(150) NULL,
    away_team_id BIGINT NULL,
    away_name VARCHAR(150) NULL,
    starts_at DATETIME(6) NOT NULL,
    venue VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL,
    home_score VARCHAR(50) NULL,
    away_score VARCHAR(50) NULL,
    result VARCHAR(20) NULL,
    recorded_at DATETIME(6) NULL,
    recorded_by_account_id BIGINT NULL,
    CONSTRAINT pk_campus_fixtures PRIMARY KEY (id),
    CONSTRAINT fk_campus_fixtures_tournament FOREIGN KEY (tournament_id) REFERENCES campus_tournaments (id),
    CONSTRAINT fk_campus_fixtures_home_team FOREIGN KEY (home_team_id) REFERENCES campus_teams (id),
    CONSTRAINT fk_campus_fixtures_away_team FOREIGN KEY (away_team_id) REFERENCES campus_teams (id),
    CONSTRAINT fk_campus_fixtures_recorded_by FOREIGN KEY (recorded_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_campus_fixtures_home CHECK ((home_team_id IS NULL) <> (home_name IS NULL)),
    CONSTRAINT ck_campus_fixtures_away CHECK ((away_team_id IS NULL) <> (away_name IS NULL)),
    CONSTRAINT ck_campus_fixtures_status CHECK (status IN ('SCHEDULED', 'PLAYED', 'CANCELLED')),
    CONSTRAINT ck_campus_fixtures_result CHECK (result IS NULL OR result IN ('HOME_WIN', 'AWAY_WIN', 'DRAW')),
    CONSTRAINT ck_campus_fixtures_played CHECK ((status = 'PLAYED') = (result IS NOT NULL)),
    INDEX ix_campus_fixtures_tournament (tournament_id, starts_at)
);
