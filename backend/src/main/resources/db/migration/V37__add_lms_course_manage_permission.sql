-- Lets a faculty account create the LMS course of a course offering it teaches, publish or archive it, and
-- synchronize its students' LMS memberships from official course enrollments. It does not decide which offerings:
-- official teaching assignments do.
INSERT INTO permissions (code) VALUES ('LMS_COURSE_MANAGE');

-- Given to the FACULTY role only; STUDENT and ADMIN get nothing. Both rows are looked up by name, never by ID, and
-- a missing role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'LMS_COURSE_MANAGE')
);
