-- v0.10.4 to v0.10.6: one secure pass service, hostel outings (gate passes and leave) with extensions, and the gate's
-- movement log. A pass code is random and shown only to its holder; only its SHA-256 fingerprint is stored. A pass is
-- valid in its time window until revoked. Outings take their approval from the approval engine; their status is never
-- copied. Whether a student is inside or outside is worked out from the latest movement. Nothing here is deleted.
CREATE TABLE passes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purpose VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    holder_user_account_id BIGINT NOT NULL,
    code_fingerprint CHAR(64) NOT NULL,
    valid_from DATETIME(6) NOT NULL,
    valid_until DATETIME(6) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_passes_fingerprint UNIQUE (code_fingerprint),
    CONSTRAINT fk_passes_holder FOREIGN KEY (holder_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_passes_window CHECK (valid_until > valid_from),
    INDEX ix_passes_source (purpose, source_id, revoked_at)
);

CREATE TABLE hostel_outings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_profile_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    leave_from DATETIME(6) NOT NULL,
    leave_until DATETIME(6) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    destination VARCHAR(200) NULL,
    approval_request_id BIGINT NULL,
    returned_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_outings_student FOREIGN KEY (student_profile_id) REFERENCES student_profiles (id),
    CONSTRAINT fk_hostel_outings_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT uk_hostel_outings_approval UNIQUE (approval_request_id),
    CONSTRAINT ck_hostel_outings_kind CHECK (kind IN ('GATE_PASS', 'LEAVE')),
    CONSTRAINT ck_hostel_outings_window CHECK (leave_until > leave_from),
    INDEX ix_hostel_outings_student (student_profile_id, created_at)
);

CREATE TABLE hostel_outing_extensions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    outing_id BIGINT NOT NULL,
    requested_until DATETIME(6) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    approval_request_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_outing_extensions_outing FOREIGN KEY (outing_id) REFERENCES hostel_outings (id),
    CONSTRAINT fk_hostel_outing_extensions_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT uk_hostel_outing_extensions_approval UNIQUE (approval_request_id)
);

CREATE TABLE gate_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_user_account_id BIGINT NOT NULL,
    outing_id BIGINT NULL,
    direction VARCHAR(10) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    recorded_by_user_account_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_gate_movements_student FOREIGN KEY (student_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_gate_movements_outing FOREIGN KEY (outing_id) REFERENCES hostel_outings (id),
    CONSTRAINT fk_gate_movements_recorder FOREIGN KEY (recorded_by_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_gate_movements_direction CHECK (direction IN ('OUT', 'IN')),
    INDEX ix_gate_movements_student (student_user_account_id, recorded_at),
    INDEX ix_gate_movements_recorded (recorded_at)
);

-- The workflow outings and their extensions use: one step, decided by the student's hostel warden. Reference data,
-- created only when missing.
INSERT INTO approval_workflow_definitions (code, name, description, active, created_at, updated_at)
SELECT 'HOSTEL_OUTING', 'Hostel Outing Approval', 'Approval of a hostel resident''s gate pass, leave, or leave extension.',
       TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_workflow_definitions WHERE code = 'HOSTEL_OUTING');

INSERT INTO approval_workflow_step_definitions (workflow_definition_id, step_order, name, approver_role_id,
    approver_relationship, created_at, updated_at)
SELECT workflow.id, 1, 'Warden Approval', warden.id, 'STUDENT_HOSTEL_WARDEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM approval_workflow_definitions workflow JOIN roles warden ON warden.name = 'WARDEN'
WHERE workflow.code = 'HOSTEL_OUTING'
  AND NOT EXISTS (SELECT 1 FROM approval_workflow_step_definitions existing WHERE existing.workflow_definition_id = workflow.id);

-- HOSTEL_OUTING_REQUEST: residents ask for gate passes, leave, and extensions. GATE_PASS_VERIFY: security staff check
-- passes and record movements. Wardens decide the outing steps assigned to them. Roles and permissions by name.
INSERT INTO permissions (code) VALUES ('HOSTEL_OUTING_REQUEST'), ('GATE_PASS_VERIFY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_OUTING_REQUEST' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'GATE_PASS_VERIFY' WHERE r.name = 'SECURITY';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('APPROVAL_TASK_VIEW', 'APPROVAL_TASK_DECIDE')
WHERE r.name = 'WARDEN';

-- v0.10.8: hostel complaints are helpdesk tickets of the HOSTEL category, routed to the MAINTENANCE team, with the
-- room attached. Reference data, created only when missing; admins can rename, reroute, or deactivate them later.
INSERT INTO helpdesk_teams (code, name, active, created_at, updated_at)
SELECT 'MAINTENANCE', 'Hostel maintenance', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM helpdesk_teams WHERE code = 'MAINTENANCE');

INSERT INTO helpdesk_categories (code, name, description, active, created_at, updated_at, default_team_id)
SELECT 'HOSTEL', 'Hostel room and facilities', 'Problems in your hostel room or building, sent with your room.', TRUE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), (SELECT id FROM helpdesk_teams WHERE code = 'MAINTENANCE')
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM helpdesk_categories WHERE code = 'HOSTEL');
