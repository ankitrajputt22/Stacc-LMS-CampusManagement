-- v0.2.5: one append-only record of sensitive actions: who did what to which record, when, and in which request.
-- Rows are never changed or deleted. Details are short plain text without passwords, hashes, tokens, or contact data.
CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    actor_account_id BIGINT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NULL,
    details VARCHAR(500) NULL,
    request_id VARCHAR(64) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_account_id) REFERENCES user_accounts (id),
    INDEX ix_audit_events_occurred (occurred_at),
    INDEX ix_audit_events_target (target_type, target_id),
    INDEX ix_audit_events_actor (actor_account_id),
    INDEX ix_audit_events_action (action)
);

INSERT INTO permissions (code) VALUES ('AUDIT_LOG_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'AUDIT_LOG_VIEW'));
