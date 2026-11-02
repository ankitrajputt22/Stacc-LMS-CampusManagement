-- v0.2.6: admins find, create, and manage accounts: details, status, roles, temporary passwords, and the student or
-- faculty profile of an account.
INSERT INTO permissions (code) VALUES ('USER_VIEW'), ('USER_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'USER_VIEW'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'USER_MANAGE'));
