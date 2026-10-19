-- Lets a faculty account read its dashboard: a read-only overview of the course offerings it teaches, upcoming
-- assignments, submissions waiting for a grade, and open attendance sessions. It does not decide what is shown:
-- official teaching assignments and the existing LMS and attendance rules do.
INSERT INTO permissions (code) VALUES ('FACULTY_DASHBOARD_VIEW');

-- Given to the FACULTY role only; STUDENT and ADMIN get nothing. Both rows are looked up by name, never by ID, and a
-- missing role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'FACULTY_DASHBOARD_VIEW')
);
