-- The helpdesk ticket engine. Categories are configurable data with a unique, fixed code; they are made inactive
-- instead of deleted. A ticket belongs to one requester and one category, may be assigned to one admin account, and
-- gets its number (STC-000042) from its ID in the same transaction that creates it. Messages and history entries are
-- append-only. Nothing cascades and nothing is deleted.
CREATE TABLE helpdesk_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_helpdesk_categories_code UNIQUE (code),
    INDEX idx_helpdesk_categories_active_name (active, name)
);

-- ticket_number is empty only inside the transaction that creates the ticket, between the insert and the update
-- that sets it from the generated ID.
CREATE TABLE helpdesk_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_number VARCHAR(20) NULL,
    requester_user_account_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_to_user_account_id BIGINT NULL,
    resolved_at DATETIME(6) NULL,
    closed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_helpdesk_tickets_ticket_number UNIQUE (ticket_number),
    CONSTRAINT fk_helpdesk_tickets_requester FOREIGN KEY (requester_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_helpdesk_tickets_category FOREIGN KEY (category_id) REFERENCES helpdesk_categories (id),
    CONSTRAINT fk_helpdesk_tickets_assignee FOREIGN KEY (assigned_to_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_helpdesk_tickets_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_helpdesk_tickets_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_helpdesk_tickets_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    INDEX idx_helpdesk_tickets_requester_created (requester_user_account_id, created_at, id),
    INDEX idx_helpdesk_tickets_status_created (status, created_at, id),
    INDEX idx_helpdesk_tickets_assignee_status (assigned_to_user_account_id, status),
    INDEX idx_helpdesk_tickets_category (category_id),
    INDEX idx_helpdesk_tickets_created (created_at, id)
);

CREATE TABLE helpdesk_ticket_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    author_user_account_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_helpdesk_ticket_messages_ticket FOREIGN KEY (ticket_id) REFERENCES helpdesk_tickets (id),
    CONSTRAINT fk_helpdesk_ticket_messages_author FOREIGN KEY (author_user_account_id) REFERENCES user_accounts (id),
    INDEX idx_helpdesk_ticket_messages_ticket_created (ticket_id, created_at, id)
);

CREATE TABLE helpdesk_ticket_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    actor_user_account_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    from_value VARCHAR(100) NULL,
    to_value VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_helpdesk_ticket_history_ticket FOREIGN KEY (ticket_id) REFERENCES helpdesk_tickets (id),
    CONSTRAINT fk_helpdesk_ticket_history_actor FOREIGN KEY (actor_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_helpdesk_ticket_history_action CHECK (action IN ('CREATED', 'MESSAGE_ADDED', 'STATUS_CHANGED',
        'PRIORITY_CHANGED', 'ASSIGNED', 'UNASSIGNED')),
    INDEX idx_helpdesk_ticket_history_ticket_created (ticket_id, created_at, id)
);
