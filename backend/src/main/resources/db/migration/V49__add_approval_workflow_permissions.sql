-- Approval permissions. Every account may read its own approval requests. FACULTY and ADMIN may see and decide
-- approval tasks, but only the steps that require a role they actually hold: the permission alone decides nothing, and
-- ADMIN gets no pass on FACULTY steps. Only ADMIN configures workflows.
INSERT INTO permissions (code) VALUES
    ('APPROVAL_REQUEST_VIEW_OWN'),
    ('APPROVAL_TASK_VIEW'),
    ('APPROVAL_TASK_DECIDE'),
    ('APPROVAL_WORKFLOW_MANAGE');

-- Every row is looked up by name, never by ID, and a missing role makes a statement fail instead of quietly
-- assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_REQUEST_VIEW_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_REQUEST_VIEW_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_REQUEST_VIEW_OWN')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_TASK_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_TASK_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_TASK_DECIDE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_TASK_DECIDE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'APPROVAL_WORKFLOW_MANAGE')
);
