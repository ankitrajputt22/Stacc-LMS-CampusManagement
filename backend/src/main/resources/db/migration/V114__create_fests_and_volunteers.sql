-- v0.11.7: fests. The campus office runs a fest (a few days of the college calendar), puts campus events on its
-- schedule (an event belongs to at most one fest), and opens volunteer shifts with a number of places. Students and
-- faculty apply for shifts; the office accepts within the places (under the shift row lock) or declines. Fests are
-- archived, applications withdrawn or declined; nothing is deleted.
CREATE TABLE campus_fests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_fests_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_campus_fests_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_campus_fests_dates CHECK (starts_on <= ends_on)
);

CREATE TABLE campus_fest_events (
    fest_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    PRIMARY KEY (fest_id, event_id),
    CONSTRAINT fk_campus_fest_events_fest FOREIGN KEY (fest_id) REFERENCES campus_fests (id),
    CONSTRAINT fk_campus_fest_events_event FOREIGN KEY (event_id) REFERENCES campus_events (id),
    CONSTRAINT uq_campus_fest_events_event UNIQUE (event_id)
);

CREATE TABLE campus_fest_shifts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fest_id BIGINT NOT NULL,
    role_title VARCHAR(150) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    places INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_fest_shifts_fest FOREIGN KEY (fest_id) REFERENCES campus_fests (id),
    CONSTRAINT ck_campus_fest_shifts_places CHECK (places BETWEEN 1 AND 500),
    CONSTRAINT ck_campus_fest_shifts_order CHECK (starts_at < ends_at),
    INDEX ix_campus_fest_shifts_fest (fest_id, starts_at)
);

CREATE TABLE campus_fest_volunteers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shift_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(10) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    -- One application per person and shift while it is still open or accepted.
    live_key VARCHAR(40) AS (CASE WHEN status IN ('APPLIED', 'ACCEPTED') THEN CONCAT(shift_id, ':', account_id) END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_fest_volunteers_shift FOREIGN KEY (shift_id) REFERENCES campus_fest_shifts (id),
    CONSTRAINT fk_campus_fest_volunteers_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_campus_fest_volunteers_live UNIQUE (live_key),
    CONSTRAINT ck_campus_fest_volunteers_status CHECK (status IN ('APPLIED', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    INDEX ix_campus_fest_volunteers_account (account_id)
);
