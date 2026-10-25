-- The approval workflow engine. A workflow definition has ordered steps, each decided by one role. An approval request
-- is one run of a workflow for a business record, named loosely by source type and ID with a short subject; it copies
-- the workflow's steps when it starts, so later edits never change it. History is append-only. Nothing that holds
-- approval state cascades or is deleted. There is deliberately no unique key on the source: a record may need a second
-- round of approval, and the owning module decides that.
CREATE TABLE approval_workflow_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_approval_workflow_definitions_code UNIQUE (code)
);

-- The steps are configuration: replacing a workflow's steps changes these rows, and no request points at them.
CREATE TABLE approval_workflow_step_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workflow_definition_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    approver_role_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_step_definitions_workflow FOREIGN KEY (workflow_definition_id) REFERENCES approval_workflow_definitions (id),
    CONSTRAINT fk_approval_step_definitions_role FOREIGN KEY (approver_role_id) REFERENCES roles (id),
    CONSTRAINT uk_approval_step_definitions_order UNIQUE (workflow_definition_id, step_order),
    CONSTRAINT ck_approval_step_definitions_order CHECK (step_order >= 1)
);

CREATE TABLE approval_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workflow_definition_id BIGINT NOT NULL,
    requester_user_account_id BIGINT NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_step_order INT NULL,
    submitted_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_requests_workflow FOREIGN KEY (workflow_definition_id) REFERENCES approval_workflow_definitions (id),
    CONSTRAINT fk_approval_requests_requester FOREIGN KEY (requester_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_approval_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    -- A pending request points at its current step and is not complete; a finished one points at none and is.
    CONSTRAINT ck_approval_requests_state CHECK (
        (status = 'PENDING' AND current_step_order IS NOT NULL AND completed_at IS NULL)
        OR (status <> 'PENDING' AND current_step_order IS NULL AND completed_at IS NOT NULL)),
    INDEX idx_approval_requests_requester_created (requester_user_account_id, created_at),
    INDEX idx_approval_requests_status_created (status, created_at),
    INDEX idx_approval_requests_workflow_status (workflow_definition_id, status),
    INDEX idx_approval_requests_source (source_type, source_id)
);

CREATE TABLE approval_request_steps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    approval_request_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    step_name VARCHAR(150) NOT NULL,
    required_role_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    acted_by_user_account_id BIGINT NULL,
    acted_at DATETIME(6) NULL,
    comment TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_request_steps_request FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT fk_approval_request_steps_actor FOREIGN KEY (acted_by_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_approval_request_steps_order UNIQUE (approval_request_id, step_order),
    CONSTRAINT ck_approval_request_steps_status CHECK (status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    -- Who decided and when is recorded exactly for decided steps.
    CONSTRAINT ck_approval_request_steps_decision CHECK (
        (status IN ('APPROVED', 'REJECTED')) = (acted_by_user_account_id IS NOT NULL AND acted_at IS NOT NULL)),
    INDEX idx_approval_request_steps_queue (status, required_role_code, created_at)
);

CREATE TABLE approval_request_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    approval_request_id BIGINT NOT NULL,
    actor_user_account_id BIGINT NULL,
    action VARCHAR(30) NOT NULL,
    step_order INT NULL,
    comment TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_request_history_request FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT fk_approval_request_history_actor FOREIGN KEY (actor_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_approval_request_history_action CHECK (action IN ('REQUEST_CREATED', 'STEP_ACTIVATED', 'STEP_APPROVED',
        'STEP_REJECTED', 'REQUEST_APPROVED', 'REQUEST_REJECTED', 'REQUEST_CANCELLED')),
    INDEX idx_approval_request_history_request_created (approval_request_id, created_at)
);
