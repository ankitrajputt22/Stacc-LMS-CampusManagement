-- v0.16.7: the admins' system health page.
INSERT INTO permissions (code) VALUES ('SYSTEM_HEALTH_VIEW');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'SYSTEM_HEALTH_VIEW' WHERE r.name = 'ADMIN';
