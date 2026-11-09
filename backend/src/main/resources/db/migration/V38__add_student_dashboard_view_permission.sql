-- Lets a student account read its dashboard: a read-only overview of its current LMS courses, upcoming assignments,
-- recent announcements, finalized attendance, and published results. It does not decide what is shown: the existing
-- student course, attendance, and result rules do.
INSERT INTO permissions (code) VALUES ('STUDENT_DASHBOARD_VIEW');

-- Given to the STUDENT role only; FACULTY and ADMIN get nothing. Both rows are looked up by name, never by ID, and a
-- missing role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'STUDENT_DASHBOARD_VIEW')
);
