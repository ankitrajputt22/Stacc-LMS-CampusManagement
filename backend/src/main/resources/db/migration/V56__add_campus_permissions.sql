-- Campus permissions. Students and faculty request campus services, see and cancel their own requests, and read
-- campus notices. ADMIN manages the service catalog and requests, and reads and manages notices. Deciding an approval
-- is not one of these: it needs the approval engine's APPROVAL_TASK_DECIDE and the step's role, so
-- CAMPUS_SERVICE_MANAGE is no approval pass.
INSERT INTO permissions (code) VALUES
    ('CAMPUS_SERVICE_REQUEST_CREATE'),
    ('CAMPUS_SERVICE_REQUEST_VIEW_OWN'),
    ('CAMPUS_SERVICE_REQUEST_CANCEL'),
    ('CAMPUS_SERVICE_MANAGE'),
    ('CAMPUS_NOTICE_VIEW'),
    ('CAMPUS_NOTICE_MANAGE');

-- Every row is looked up by name, never by ID, and a missing role makes a statement fail instead of quietly
-- assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'CAMPUS_SERVICE_REQUEST_CREATE'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'CAMPUS_SERVICE_REQUEST_VIEW_OWN'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'CAMPUS_SERVICE_REQUEST_CANCEL'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'CAMPUS_NOTICE_VIEW'));

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'CAMPUS_SERVICE_REQUEST_CREATE'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'CAMPUS_SERVICE_REQUEST_VIEW_OWN'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'CAMPUS_SERVICE_REQUEST_CANCEL'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'CAMPUS_NOTICE_VIEW'));

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'CAMPUS_SERVICE_MANAGE'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'CAMPUS_NOTICE_VIEW'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'CAMPUS_NOTICE_MANAGE'));
