-- Hostel permissions. Students apply for hostel, see their own applications, and cancel pending ones. ADMIN manages
-- the hostel inventory, applications, and allocations. Approving an application is not one of these: it needs the
-- approval engine's APPROVAL_TASK_DECIDE and the role of the workflow step, so HOSTEL_MANAGE is no approval pass.
INSERT INTO permissions (code) VALUES
    ('HOSTEL_APPLICATION_CREATE'),
    ('HOSTEL_APPLICATION_VIEW_OWN'),
    ('HOSTEL_APPLICATION_CANCEL'),
    ('HOSTEL_MANAGE');

-- Every row is looked up by name, never by ID, and a missing role makes a statement fail instead of quietly
-- assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'HOSTEL_APPLICATION_CREATE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'HOSTEL_APPLICATION_VIEW_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'HOSTEL_APPLICATION_CANCEL')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'HOSTEL_MANAGE')
);
