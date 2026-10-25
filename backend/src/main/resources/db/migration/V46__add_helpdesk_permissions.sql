-- Helpdesk permissions. Every account holding STUDENT, FACULTY, or ADMIN may raise tickets and see and answer its
-- own. Only ADMIN sees, answers, and manages every ticket, through explicit permissions and never through the role
-- alone.
INSERT INTO permissions (code) VALUES
    ('HELPDESK_TICKET_CREATE'),
    ('HELPDESK_TICKET_VIEW_OWN'),
    ('HELPDESK_TICKET_REPLY_OWN'),
    ('HELPDESK_TICKET_VIEW_ALL'),
    ('HELPDESK_TICKET_MANAGE'),
    ('HELPDESK_TICKET_REPLY_ALL');

-- Every row is looked up by name, never by ID, and a missing role makes a statement fail instead of quietly
-- assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_CREATE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_CREATE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_CREATE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_VIEW_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_VIEW_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_VIEW_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_REPLY_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_REPLY_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_REPLY_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_VIEW_ALL')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_MANAGE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'HELPDESK_TICKET_REPLY_ALL')
);
