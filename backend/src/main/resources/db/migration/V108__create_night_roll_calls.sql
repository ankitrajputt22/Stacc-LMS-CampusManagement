-- v0.10.7: night roll calls. A warden opens one sheet per hostel (or block) and night; it lists the residents allocated
-- there at that moment, pre-filled from the gate log: a resident whose last movement was going out starts as OUT,
-- everyone else as UNMARKED. Nothing starts as ABSENT. A finalized sheet no longer changes; nothing is deleted.
CREATE TABLE hostel_roll_calls (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_id BIGINT NOT NULL,
    block VARCHAR(50) NULL,
    roll_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finalized_at DATETIME(6) NULL,
    -- One sheet per hostel, block (or the whole hostel), and night.
    sheet_key VARCHAR(80) AS (CONCAT(hostel_id, ':', COALESCE(block, ''), ':', roll_date)) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_roll_calls_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_hostel_roll_calls_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hostel_roll_calls_sheet UNIQUE (sheet_key),
    CONSTRAINT ck_hostel_roll_calls_status CHECK (status IN ('OPEN', 'FINALIZED')),
    CONSTRAINT ck_hostel_roll_calls_finalized CHECK ((status = 'FINALIZED') = (finalized_at IS NOT NULL)),
    INDEX ix_hostel_roll_calls_hostel (hostel_id, roll_date)
);

CREATE TABLE hostel_roll_call_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    roll_call_id BIGINT NOT NULL,
    allocation_id BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL,
    prefilled_out BOOLEAN NOT NULL,
    marked_by_account_id BIGINT NULL,
    marked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_roll_call_entries_roll_call FOREIGN KEY (roll_call_id) REFERENCES hostel_roll_calls (id),
    CONSTRAINT fk_hostel_roll_call_entries_allocation FOREIGN KEY (allocation_id) REFERENCES hostel_allocations (id),
    CONSTRAINT fk_hostel_roll_call_entries_marked_by FOREIGN KEY (marked_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hostel_roll_call_entries_resident UNIQUE (roll_call_id, allocation_id),
    CONSTRAINT ck_hostel_roll_call_entries_status CHECK (status IN ('UNMARKED', 'PRESENT', 'ABSENT', 'OUT'))
);

-- HOSTEL_ROLL_CALL: wardens take night roll calls of their own hostels. By name.
INSERT INTO permissions (code) VALUES ('HOSTEL_ROLL_CALL');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_ROLL_CALL' WHERE r.name = 'WARDEN';
