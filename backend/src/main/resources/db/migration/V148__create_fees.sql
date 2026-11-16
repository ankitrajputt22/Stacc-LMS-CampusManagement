-- v0.16.7: fees, as records only (no payment gateway). A program's fee structure for an academic session lists fee
-- heads with amounts and one due date. The accounts office (FEE_MANAGE) records payments, each with its receipt number;
-- students (FEE_VIEW_OWN) see their fees and payments, apply for scholarships, and ask for refunds of money paid beyond
-- what they owe. What is due is worked out on read: total, less payments and approved scholarships, plus approved
-- refunds. Payments, scholarships, and refunds are never deleted; decisions lock the student's account row first.
CREATE TABLE fee_structures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    academic_session_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_fee_structures PRIMARY KEY (id),
    CONSTRAINT fk_fee_structures_program FOREIGN KEY (program_id) REFERENCES programs (id),
    CONSTRAINT fk_fee_structures_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT uq_fee_structures UNIQUE (program_id, academic_session_id)
);

CREATE TABLE fee_structure_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fee_structure_id BIGINT NOT NULL,
    head VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT pk_fee_structure_items PRIMARY KEY (id),
    CONSTRAINT fk_fee_structure_items_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structures (id),
    CONSTRAINT uq_fee_structure_items UNIQUE (fee_structure_id, head),
    CONSTRAINT ck_fee_structure_items_head CHECK (head IN ('TUITION', 'EXAMINATION', 'LIBRARY', 'LABORATORY', 'HOSTEL', 'MESS', 'OTHER')),
    CONSTRAINT ck_fee_structure_items_amount CHECK (amount >= 0)
);

CREATE TABLE fee_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_account_id BIGINT NOT NULL,
    academic_session_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    reference VARCHAR(60) NULL,
    paid_on DATE NOT NULL,
    recorded_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_fee_payments PRIMARY KEY (id),
    CONSTRAINT fk_fee_payments_student FOREIGN KEY (student_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_fee_payments_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_fee_payments_recorder FOREIGN KEY (recorded_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_fee_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_fee_payments_mode CHECK (mode IN ('UPI', 'BANK_TRANSFER', 'CARD', 'CASH', 'CHEQUE')),
    INDEX ix_fee_payments_student (student_account_id, academic_session_id)
);

CREATE TABLE fee_adjustments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(20) NOT NULL,
    student_account_id BIGINT NOT NULL,
    academic_session_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    decision_comment VARCHAR(1000) NULL,
    decided_by_account_id BIGINT NULL,
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_fee_adjustments PRIMARY KEY (id),
    CONSTRAINT fk_fee_adjustments_student FOREIGN KEY (student_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_fee_adjustments_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_fee_adjustments_decider FOREIGN KEY (decided_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_fee_adjustments_kind CHECK (kind IN ('SCHOLARSHIP', 'REFUND')),
    CONSTRAINT ck_fee_adjustments_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_fee_adjustments_amount CHECK (amount > 0),
    INDEX ix_fee_adjustments_student (student_account_id, academic_session_id),
    INDEX ix_fee_adjustments_status (kind, status, created_at)
);

INSERT INTO permissions (code) VALUES ('FEE_VIEW_OWN'), ('FEE_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'FEE_VIEW_OWN' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'FEE_MANAGE' WHERE r.name = 'ADMIN';
