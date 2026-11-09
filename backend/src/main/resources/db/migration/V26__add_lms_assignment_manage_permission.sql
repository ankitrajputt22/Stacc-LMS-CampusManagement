-- Lets a faculty account create, edit, publish, and archive the assignments of LMS courses. It does not
-- decide which courses: official teaching assignments do.
INSERT INTO permissions (code) VALUES ('LMS_ASSIGNMENT_MANAGE');

-- Given to the FACULTY role only. Both rows are looked up by name, never by ID, and a missing
-- role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'LMS_ASSIGNMENT_MANAGE')
);
