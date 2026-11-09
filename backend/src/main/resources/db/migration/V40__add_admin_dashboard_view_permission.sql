-- Lets an admin account read its dashboard: a read-only overview of the academic masters, course offerings, and
-- enrollments. It gives no right to change any of them; their own permissions stay as they are.
INSERT INTO permissions (code) VALUES ('ADMIN_DASHBOARD_VIEW');

-- Given to the ADMIN role only; STUDENT and FACULTY get nothing. Both rows are looked up by name, never by ID, and a
-- missing role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'ADMIN_DASHBOARD_VIEW')
);
