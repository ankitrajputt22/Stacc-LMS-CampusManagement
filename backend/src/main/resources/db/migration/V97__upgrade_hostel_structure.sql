-- v0.10.1 and v0.10.2: room details (block, floor, type, notes), beds under maintenance, wardens assigned to a hostel
-- (optionally one block), a student's hostel preference (a wish only: the office still allocates), and check-in and
-- vacating notes on allocations. Warden assignments end instead of being deleted.
ALTER TABLE hostel_rooms ADD COLUMN block VARCHAR(50) NULL;
ALTER TABLE hostel_rooms ADD COLUMN floor_number INT NULL;
ALTER TABLE hostel_rooms ADD COLUMN room_type VARCHAR(50) NULL;
ALTER TABLE hostel_rooms ADD COLUMN notes VARCHAR(500) NULL;
ALTER TABLE hostel_rooms ADD CONSTRAINT ck_hostel_rooms_floor CHECK (floor_number IS NULL OR floor_number BETWEEN -5 AND 200);

ALTER TABLE hostel_beds ADD COLUMN under_maintenance BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE hostel_wardens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    block VARCHAR(50) NULL,
    assigned_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_wardens_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_hostel_wardens_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    INDEX ix_hostel_wardens_account (user_account_id, ended_at),
    INDEX ix_hostel_wardens_hostel (hostel_id, ended_at)
);

ALTER TABLE hostel_applications ADD COLUMN preferred_hostel_id BIGINT NULL;
ALTER TABLE hostel_applications ADD COLUMN preference_note VARCHAR(500) NULL;
ALTER TABLE hostel_applications
    ADD CONSTRAINT fk_hostel_applications_preferred FOREIGN KEY (preferred_hostel_id) REFERENCES hostels (id);

ALTER TABLE hostel_allocations ADD COLUMN checked_in_at DATETIME(6) NULL;
ALTER TABLE hostel_allocations ADD COLUMN check_in_note VARCHAR(500) NULL;
ALTER TABLE hostel_allocations ADD COLUMN vacate_condition_note VARCHAR(500) NULL;

-- HOSTEL_WARDEN_VIEW: a warden's dashboard, bed map, and check-in for the hostels they are assigned to. Roles by name.
INSERT INTO permissions (code) VALUES ('HOSTEL_WARDEN_VIEW');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_WARDEN_VIEW' WHERE r.name = 'WARDEN';
