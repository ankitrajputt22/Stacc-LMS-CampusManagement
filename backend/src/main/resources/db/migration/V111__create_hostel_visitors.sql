-- v0.10.11: hostel visitors. The hostel office sets each hostel's visiting hours; a resident registers a visitor for
-- a day ahead; security records pre-registered and walk-in visitors arriving (only within visiting hours) and leaving.
-- Only the visitor's name and relation are kept; visits are cancelled or closed, never deleted.
CREATE TABLE hostel_visiting_hours (
    hostel_id BIGINT NOT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (hostel_id),
    CONSTRAINT fk_hostel_visiting_hours_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT ck_hostel_visiting_hours_order CHECK (opens_at < closes_at)
);

CREATE TABLE hostel_visits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    allocation_id BIGINT NOT NULL,
    visitor_name VARCHAR(120) NOT NULL,
    relation VARCHAR(60) NOT NULL,
    kind VARCHAR(15) NOT NULL,
    expected_on DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    arrived_at DATETIME(6) NULL,
    left_at DATETIME(6) NULL,
    recorded_by_account_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_visits_allocation FOREIGN KEY (allocation_id) REFERENCES hostel_allocations (id),
    CONSTRAINT fk_hostel_visits_recorded_by FOREIGN KEY (recorded_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_hostel_visits_kind CHECK (kind IN ('PRE_REGISTERED', 'WALK_IN')),
    CONSTRAINT ck_hostel_visits_status CHECK (status IN ('EXPECTED', 'ARRIVED', 'LEFT', 'CANCELLED')),
    CONSTRAINT ck_hostel_visits_arrived CHECK ((status IN ('ARRIVED', 'LEFT')) = (arrived_at IS NOT NULL)),
    CONSTRAINT ck_hostel_visits_left CHECK ((status = 'LEFT') = (left_at IS NOT NULL)),
    INDEX ix_hostel_visits_day (expected_on, status),
    INDEX ix_hostel_visits_allocation (allocation_id, expected_on)
);

-- HOSTEL_VISITOR_REGISTER: residents register their visitors. HOSTEL_VISITOR_DESK: security records arrivals,
-- departures, and walk-ins. Roles by name.
INSERT INTO permissions (code) VALUES ('HOSTEL_VISITOR_REGISTER'), ('HOSTEL_VISITOR_DESK');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_VISITOR_REGISTER' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_VISITOR_DESK' WHERE r.name = 'SECURITY';
