-- Campus services and notices. A service definition is configurable catalog data with a fixed code; a service request
-- belongs to one service and one requesting account and, when its service needs approval, to one approval request of
-- the approval engine. A request keeps its own business status; while it is SUBMITTED, the approval's outcome decides
-- what it shows. Notices are the campus notice board. Nothing cascades and nothing is deleted.
CREATE TABLE campus_service_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(3000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    available_to_student BOOLEAN NOT NULL,
    available_to_faculty BOOLEAN NOT NULL,
    approval_required BOOLEAN NOT NULL,
    approval_workflow_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_service_definitions_code UNIQUE (code),
    CONSTRAINT ck_campus_service_definitions_audience CHECK (available_to_student OR available_to_faculty),
    CONSTRAINT ck_campus_service_definitions_workflow CHECK (
        (approval_required AND approval_workflow_code IS NOT NULL)
        OR (NOT approval_required AND approval_workflow_code IS NULL)),
    INDEX idx_campus_service_definitions_active (active)
);

-- request_number is empty only inside the transaction that creates the request, until its ID is known. The unique key
-- on approval_request_id is also its index.
CREATE TABLE campus_service_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_number VARCHAR(20) NULL,
    service_definition_id BIGINT NOT NULL,
    requester_user_account_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    details TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    approval_request_id BIGINT NULL,
    submitted_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_service_requests_number UNIQUE (request_number),
    CONSTRAINT uk_campus_service_requests_approval UNIQUE (approval_request_id),
    CONSTRAINT fk_campus_service_requests_service FOREIGN KEY (service_definition_id)
        REFERENCES campus_service_definitions (id),
    CONSTRAINT fk_campus_service_requests_requester FOREIGN KEY (requester_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_campus_service_requests_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT ck_campus_service_requests_status CHECK (
        status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_campus_service_requests_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    INDEX idx_campus_service_requests_requester (requester_user_account_id, submitted_at),
    INDEX idx_campus_service_requests_service (service_definition_id, created_at),
    INDEX idx_campus_service_requests_status (status, created_at)
);

CREATE TABLE campus_notices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    visible_to_student BOOLEAN NOT NULL,
    visible_to_faculty BOOLEAN NOT NULL,
    visible_to_admin BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_campus_notices_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_campus_notices_audience CHECK (visible_to_student OR visible_to_faculty OR visible_to_admin),
    CONSTRAINT ck_campus_notices_published CHECK (status = 'DRAFT' OR status = 'ARCHIVED' OR published_at IS NOT NULL),
    INDEX idx_campus_notices_status_published (status, published_at)
);
