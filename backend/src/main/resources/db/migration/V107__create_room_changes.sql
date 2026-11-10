-- v0.10.3: hostel room changes. A resident asks to move, or proposes a swap of beds with another resident, who must
-- accept first. The resident's hostel warden decides through the HOSTEL_ROOM_CHANGE workflow; approval state stays
-- with the engine. The hostel office then moves the student, or swaps both beds at once. Nothing is deleted.
CREATE TABLE hostel_room_changes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(10) NOT NULL,
    requester_allocation_id BIGINT NOT NULL,
    partner_allocation_id BIGINT NULL,
    partner_response VARCHAR(10) NULL,
    reason VARCHAR(1000) NOT NULL,
    preference VARCHAR(300) NULL,
    status VARCHAR(10) NOT NULL,
    approval_request_id BIGINT NULL,
    completed_by_account_id BIGINT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_room_changes_requester FOREIGN KEY (requester_allocation_id) REFERENCES hostel_allocations (id),
    CONSTRAINT fk_hostel_room_changes_partner FOREIGN KEY (partner_allocation_id) REFERENCES hostel_allocations (id),
    CONSTRAINT fk_hostel_room_changes_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT fk_hostel_room_changes_completed_by FOREIGN KEY (completed_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hostel_room_changes_approval UNIQUE (approval_request_id),
    CONSTRAINT ck_hostel_room_changes_kind CHECK (kind IN ('MOVE', 'SWAP')),
    CONSTRAINT ck_hostel_room_changes_partner CHECK ((kind = 'SWAP') = (partner_allocation_id IS NOT NULL)),
    CONSTRAINT ck_hostel_room_changes_response CHECK (partner_response IS NULL OR partner_response IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT ck_hostel_room_changes_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED', 'DECLINED')),
    CONSTRAINT ck_hostel_room_changes_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    INDEX ix_hostel_room_changes_requester (requester_allocation_id, status),
    INDEX ix_hostel_room_changes_partner (partner_allocation_id, status),
    INDEX ix_hostel_room_changes_status (status, created_at)
);

INSERT INTO approval_workflow_definitions (code, name, description, active, created_at, updated_at)
SELECT 'HOSTEL_ROOM_CHANGE', 'Hostel Room Change Approval', 'Approval of a hostel resident''s room move or bed swap.',
       TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_workflow_definitions WHERE code = 'HOSTEL_ROOM_CHANGE');

INSERT INTO approval_workflow_step_definitions (workflow_definition_id, step_order, name, approver_role_id,
    approver_relationship, created_at, updated_at)
SELECT workflow.id, 1, 'Warden Approval', warden.id, 'STUDENT_HOSTEL_WARDEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM approval_workflow_definitions workflow JOIN roles warden ON warden.name = 'WARDEN'
WHERE workflow.code = 'HOSTEL_ROOM_CHANGE'
  AND NOT EXISTS (SELECT 1 FROM approval_workflow_step_definitions existing WHERE existing.workflow_definition_id = workflow.id);

-- HOSTEL_ROOM_CHANGE_REQUEST: residents ask to move, propose swaps, and answer swaps proposed to them. By name.
INSERT INTO permissions (code) VALUES ('HOSTEL_ROOM_CHANGE_REQUEST');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_ROOM_CHANGE_REQUEST' WHERE r.name = 'STUDENT';
