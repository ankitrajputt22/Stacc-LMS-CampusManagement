-- v0.9.5: support teams and routing, staff team members, internal notes, waiting on the requester, reopening a
-- resolved ticket within 7 days, ratings, and the first response time. Teams are configuration: made inactive, never
-- deleted. Ticket history, messages, and notes stay append-only.
CREATE TABLE helpdesk_teams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_helpdesk_teams_code UNIQUE (code)
);

CREATE TABLE helpdesk_team_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_helpdesk_team_members UNIQUE (team_id, user_account_id),
    CONSTRAINT fk_helpdesk_team_members_team FOREIGN KEY (team_id) REFERENCES helpdesk_teams (id),
    CONSTRAINT fk_helpdesk_team_members_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

ALTER TABLE helpdesk_categories ADD COLUMN default_team_id BIGINT NULL;
ALTER TABLE helpdesk_categories
    ADD CONSTRAINT fk_helpdesk_categories_team FOREIGN KEY (default_team_id) REFERENCES helpdesk_teams (id);

ALTER TABLE helpdesk_tickets ADD COLUMN team_id BIGINT NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN first_response_at DATETIME(6) NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN rating INT NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN rating_comment VARCHAR(1000) NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN rated_at DATETIME(6) NULL;
ALTER TABLE helpdesk_tickets ADD CONSTRAINT fk_helpdesk_tickets_team FOREIGN KEY (team_id) REFERENCES helpdesk_teams (id);
ALTER TABLE helpdesk_tickets ADD CONSTRAINT ck_helpdesk_tickets_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);
ALTER TABLE helpdesk_tickets DROP CHECK ck_helpdesk_tickets_status;
ALTER TABLE helpdesk_tickets ADD CONSTRAINT ck_helpdesk_tickets_status
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_ON_REQUESTER', 'RESOLVED', 'CLOSED'));
CREATE INDEX idx_helpdesk_tickets_team_status ON helpdesk_tickets (team_id, status, created_at);

ALTER TABLE helpdesk_ticket_messages ADD COLUMN internal BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE helpdesk_ticket_history DROP CHECK ck_helpdesk_ticket_history_action;
ALTER TABLE helpdesk_ticket_history ADD CONSTRAINT ck_helpdesk_ticket_history_action CHECK (action IN ('CREATED',
    'MESSAGE_ADDED', 'STATUS_CHANGED', 'PRIORITY_CHANGED', 'ASSIGNED', 'UNASSIGNED', 'NOTE_ADDED', 'REOPENED', 'RATED',
    'TEAM_CHANGED'));

-- HELPDESK_TEAM_WORK: staff members of a support team see and work the tickets routed to their teams. Given to the
-- staff roles whose work arrives as tickets; it is no pass to other tickets. Roles by name.
INSERT INTO permissions (code) VALUES ('HELPDESK_TEAM_WORK');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HELPDESK_TEAM_WORK'
WHERE r.name IN ('MAINTENANCE', 'WARDEN', 'LIBRARIAN');
-- Staff members also need the Inbox for their ticket notices.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'NOTIFICATION_VIEW'
WHERE r.name IN ('MAINTENANCE', 'WARDEN', 'LIBRARIAN')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
