-- Lets an account read its own Inbox and change its own read state. It opens nothing else: which notifications an
-- account has is decided by the features that sent them, and nobody can send one through the API.
INSERT INTO permissions (code) VALUES ('NOTIFICATION_VIEW');

-- NOTIFICATION_VIEW goes to STUDENT, FACULTY, and ADMIN. Every row is looked up by name, never by ID, and a missing
-- role makes a statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'NOTIFICATION_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'NOTIFICATION_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'NOTIFICATION_VIEW')
);
