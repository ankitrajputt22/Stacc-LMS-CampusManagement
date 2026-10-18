-- Lets an admin account read course offerings, one course taught in one semester.
INSERT INTO permissions (code) VALUES ('ACADEMIC_OFFERING_VIEW');

-- Lets an admin account create offerings and change their status. Nothing can delete them.
INSERT INTO permissions (code) VALUES ('ACADEMIC_OFFERING_MANAGE');

-- Lets an admin account look up students and read their official semester and course enrollments, and an
-- offering's roster.
INSERT INTO permissions (code) VALUES ('ACADEMIC_ENROLLMENT_VIEW');

-- Lets an admin account enroll students in semesters and course offerings and end those enrollments. Nothing can
-- delete them.
INSERT INTO permissions (code) VALUES ('ACADEMIC_ENROLLMENT_MANAGE');

-- All four go to ADMIN only; STUDENT and FACULTY get none. Every row is looked up by name, never by ID, and a
-- missing role makes the statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'ADMIN'), permission.id
FROM permissions permission
WHERE permission.code IN ('ACADEMIC_OFFERING_VIEW', 'ACADEMIC_OFFERING_MANAGE', 'ACADEMIC_ENROLLMENT_VIEW',
                          'ACADEMIC_ENROLLMENT_MANAGE');
