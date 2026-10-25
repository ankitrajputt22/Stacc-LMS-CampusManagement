-- Hostel management. Hostels have rooms and rooms have beds; a room's capacity is its active beds, and a bed is
-- occupied exactly when it has an ACTIVE allocation, so neither is stored. An application belongs to one student and
-- one academic session and has one approval request of the approval engine, whose status it reads instead of keeping
-- its own. Allocations keep their history. Nothing cascades and nothing is deleted.
CREATE TABLE hostels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_hostels_code UNIQUE (code)
);

CREATE TABLE hostel_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_id BIGINT NOT NULL,
    room_number VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_rooms_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT uk_hostel_rooms_number UNIQUE (hostel_id, room_number),
    INDEX idx_hostel_rooms_hostel_active (hostel_id, active)
);

CREATE TABLE hostel_beds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    bed_number VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_beds_room FOREIGN KEY (room_id) REFERENCES hostel_rooms (id),
    CONSTRAINT uk_hostel_beds_number UNIQUE (room_id, bed_number),
    INDEX idx_hostel_beds_room_active (room_id, active)
);

-- application_number and approval_request_id are empty only inside the transaction that creates the application,
-- until its ID is known and its approval has started.
CREATE TABLE hostel_applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_number VARCHAR(20) NULL,
    student_profile_id BIGINT NOT NULL,
    academic_session_id BIGINT NOT NULL,
    approval_request_id BIGINT NULL,
    note VARCHAR(3000) NULL,
    submitted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_hostel_applications_number UNIQUE (application_number),
    CONSTRAINT uk_hostel_applications_approval UNIQUE (approval_request_id),
    CONSTRAINT fk_hostel_applications_student FOREIGN KEY (student_profile_id) REFERENCES student_profiles (id),
    CONSTRAINT fk_hostel_applications_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_hostel_applications_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    INDEX idx_hostel_applications_student_session (student_profile_id, academic_session_id),
    INDEX idx_hostel_applications_session_created (academic_session_id, created_at)
);

-- At most one ACTIVE allocation per bed, per application, and per student. MySQL has no partial unique keys, so the
-- allocation service enforces this under row locks.
CREATE TABLE hostel_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_application_id BIGINT NOT NULL,
    hostel_bed_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    allocated_by_user_account_id BIGINT NOT NULL,
    allocated_at DATETIME(6) NOT NULL,
    vacated_by_user_account_id BIGINT NULL,
    vacated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_allocations_application FOREIGN KEY (hostel_application_id) REFERENCES hostel_applications (id),
    CONSTRAINT fk_hostel_allocations_bed FOREIGN KEY (hostel_bed_id) REFERENCES hostel_beds (id),
    CONSTRAINT fk_hostel_allocations_allocated_by FOREIGN KEY (allocated_by_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_hostel_allocations_vacated_by FOREIGN KEY (vacated_by_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_hostel_allocations_status CHECK (status IN ('ACTIVE', 'VACATED')),
    -- Who vacated and when is recorded exactly for vacated allocations.
    CONSTRAINT ck_hostel_allocations_vacated CHECK (
        (status = 'VACATED') = (vacated_at IS NOT NULL AND vacated_by_user_account_id IS NOT NULL)),
    INDEX idx_hostel_allocations_application_status (hostel_application_id, status),
    INDEX idx_hostel_allocations_bed_status (hostel_bed_id, status),
    INDEX idx_hostel_allocations_status_allocated (status, allocated_at)
);
