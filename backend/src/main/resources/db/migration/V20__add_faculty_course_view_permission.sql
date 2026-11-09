-- The first faculty permission. It lets a faculty account use the LMS faculty course-view feature.
-- It does not decide which courses a faculty member sees: official teaching assignments do.
INSERT INTO permissions (code) VALUES ('FACULTY_COURSE_VIEW');

-- Given to the FACULTY role only. Both rows are looked up by name, never by ID, and a missing
-- role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'FACULTY_COURSE_VIEW')
);
