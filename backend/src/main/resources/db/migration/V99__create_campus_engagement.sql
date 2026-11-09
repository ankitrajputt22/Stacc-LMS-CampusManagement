-- v0.11.1 to v0.11.5: campus engagement. Clubs and sports groups are one group model with officers and join policies;
-- facilities have opening hours and closed periods and take overlap-safe bookings (approval where needed); events have
-- an organizer, eligibility, capacity, a registration window, and a waitlist that moves up automatically. Group events
-- need their faculty advisor's approval. Approval state always comes from the approval engine. Nothing is deleted:
-- groups, facilities, and events become inactive or cancelled; memberships, bookings, and registrations keep history.
CREATE TABLE campus_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    description VARCHAR(2000) NULL,
    join_policy VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_groups_code UNIQUE (code),
    CONSTRAINT ck_campus_groups_kind CHECK (kind IN ('CLUB', 'SPORT')),
    CONSTRAINT ck_campus_groups_policy CHECK (join_policy IN ('OPEN', 'APPROVAL', 'CLOSED'))
);

CREATE TABLE campus_group_officers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    title VARCHAR(20) NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_group_officers_group FOREIGN KEY (group_id) REFERENCES campus_groups (id),
    CONSTRAINT fk_campus_group_officers_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_campus_group_officers_title CHECK (title IN ('ADVISOR', 'COACH', 'PRESIDENT', 'SECRETARY', 'OFFICER')),
    INDEX ix_campus_group_officers_group (group_id, ended_at),
    INDEX ix_campus_group_officers_account (user_account_id, ended_at)
);

CREATE TABLE campus_group_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    left_at DATETIME(6) NULL,
    current_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING', 'ACTIVE') THEN 1 END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_group_memberships_group FOREIGN KEY (group_id) REFERENCES campus_groups (id),
    CONSTRAINT fk_campus_group_memberships_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_campus_group_memberships_current UNIQUE (group_id, user_account_id, current_marker),
    CONSTRAINT ck_campus_group_memberships_status CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'LEFT')),
    INDEX ix_campus_group_memberships_account (user_account_id, status)
);

CREATE TABLE campus_facilities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    location VARCHAR(200) NULL,
    capacity INT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    needs_approval BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_facilities_code UNIQUE (code),
    CONSTRAINT ck_campus_facilities_kind CHECK (kind IN ('ROOM', 'LAB', 'HALL', 'COURT', 'GROUND', 'OTHER')),
    CONSTRAINT ck_campus_facilities_hours CHECK (closes_at > opens_at),
    CONSTRAINT ck_campus_facilities_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE TABLE campus_facility_closures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_facility_closures_facility FOREIGN KEY (facility_id) REFERENCES campus_facilities (id),
    CONSTRAINT ck_campus_facility_closures_dates CHECK (end_date >= start_date)
);

CREATE TABLE campus_bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    approval_request_id BIGINT NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_bookings_facility FOREIGN KEY (facility_id) REFERENCES campus_facilities (id),
    CONSTRAINT fk_campus_bookings_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_campus_bookings_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT ck_campus_bookings_window CHECK (ends_at > starts_at),
    INDEX ix_campus_bookings_facility_time (facility_id, starts_at, ends_at),
    INDEX ix_campus_bookings_account (user_account_id, starts_at)
);

CREATE TABLE campus_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    group_id BIGINT NULL,
    organizer_user_account_id BIGINT NOT NULL,
    venue VARCHAR(200) NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    capacity INT NULL,
    registration_opens_at DATETIME(6) NULL,
    registration_closes_at DATETIME(6) NULL,
    for_students BOOLEAN NOT NULL DEFAULT TRUE,
    for_faculty BOOLEAN NOT NULL DEFAULT FALSE,
    members_only BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    approval_request_id BIGINT NULL,
    published_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_events_group FOREIGN KEY (group_id) REFERENCES campus_groups (id),
    CONSTRAINT fk_campus_events_organizer FOREIGN KEY (organizer_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_campus_events_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT ck_campus_events_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_campus_events_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT ck_campus_events_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT ck_campus_events_audience CHECK (for_students OR for_faculty),
    INDEX ix_campus_events_status_start (status, starts_at)
);

CREATE TABLE campus_event_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    registered_at DATETIME(6) NOT NULL,
    promoted_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    current_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('REGISTERED', 'WAITLISTED') THEN 1 END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_campus_event_registrations_event FOREIGN KEY (event_id) REFERENCES campus_events (id),
    CONSTRAINT fk_campus_event_registrations_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_campus_event_registrations_current UNIQUE (event_id, user_account_id, current_marker),
    CONSTRAINT ck_campus_event_registrations_status CHECK (status IN ('REGISTERED', 'WAITLISTED', 'CANCELLED')),
    INDEX ix_campus_event_registrations_event (event_id, status, registered_at)
);

-- Workflows: bookings of facilities that need approval (one ADMIN step), and group events (the group's advisor).
INSERT INTO approval_workflow_definitions (code, name, description, active, created_at, updated_at)
SELECT 'FACILITY_BOOKING', 'Facility Booking Approval', 'Approval of a booking of a facility that needs it.', TRUE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_workflow_definitions WHERE code = 'FACILITY_BOOKING');
INSERT INTO approval_workflow_step_definitions (workflow_definition_id, step_order, name, approver_role_id, created_at, updated_at)
SELECT workflow.id, 1, 'Facilities Office', admin.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM approval_workflow_definitions workflow JOIN roles admin ON admin.name = 'ADMIN'
WHERE workflow.code = 'FACILITY_BOOKING'
  AND NOT EXISTS (SELECT 1 FROM approval_workflow_step_definitions existing WHERE existing.workflow_definition_id = workflow.id);

INSERT INTO approval_workflow_definitions (code, name, description, active, created_at, updated_at)
SELECT 'GROUP_EVENT', 'Group Event Approval', 'Approval of a club or sports group''s event by its faculty advisor.', TRUE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_workflow_definitions WHERE code = 'GROUP_EVENT');
INSERT INTO approval_workflow_step_definitions (workflow_definition_id, step_order, name, approver_role_id,
    approver_relationship, created_at, updated_at)
SELECT workflow.id, 1, 'Faculty Advisor', faculty.id, 'GROUP_ADVISOR', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM approval_workflow_definitions workflow JOIN roles faculty ON faculty.name = 'FACULTY'
WHERE workflow.code = 'GROUP_EVENT'
  AND NOT EXISTS (SELECT 1 FROM approval_workflow_step_definitions existing WHERE existing.workflow_definition_id = workflow.id);

-- CAMPUS_ENGAGEMENT_USE: joining groups, registering for events, and booking facilities. CAMPUS_ENGAGEMENT_MANAGE:
-- admins set up groups, officers, facilities, and closures, and create or cancel any event. Roles by name.
INSERT INTO permissions (code) VALUES ('CAMPUS_ENGAGEMENT_USE'), ('CAMPUS_ENGAGEMENT_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'CAMPUS_ENGAGEMENT_USE' WHERE r.name IN ('STUDENT', 'FACULTY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'CAMPUS_ENGAGEMENT_MANAGE' WHERE r.name = 'ADMIN';
