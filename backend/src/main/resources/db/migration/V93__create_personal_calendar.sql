-- v0.9.3: a person's own calendar events, private to them, and one private calendar subscription link per account.
-- Personal events are the person's own notes and may be deleted by them. The link's secret is never stored: only its
-- SHA-256 fingerprint. A revoked link stays revoked; a new link replaces it.
CREATE TABLE personal_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    notes VARCHAR(2000) NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_personal_calendar_events_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_personal_calendar_events_dates CHECK (end_date >= start_date),
    INDEX ix_personal_calendar_events_account (user_account_id, start_date)
);

CREATE TABLE calendar_feed_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    token_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at IS NULL THEN 1 END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_calendar_feed_links_fingerprint UNIQUE (token_fingerprint),
    CONSTRAINT uk_calendar_feed_links_active UNIQUE (user_account_id, active_marker),
    CONSTRAINT fk_calendar_feed_links_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

-- CALENDAR_PERSONAL_USE: one's own personal events and calendar subscription link. Roles by name.
INSERT INTO permissions (code) VALUES ('CALENDAR_PERSONAL_USE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'CALENDAR_PERSONAL_USE' WHERE r.name IN ('STUDENT', 'FACULTY', 'ADMIN');
