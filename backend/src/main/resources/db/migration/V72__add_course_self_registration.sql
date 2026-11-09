-- v0.4.2: students register for course offerings themselves and drop them before the semester's drop deadline.

-- When a student dropped the course. A dropped enrollment stays as history; the same offering is not taken again.
ALTER TABLE course_enrollments
    ADD COLUMN dropped_at DATETIME(6) NULL AFTER status;

INSERT INTO permissions (code) VALUES ('COURSE_REGISTRATION_VIEW'), ('COURSE_REGISTER'), ('COURSE_DROP');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'COURSE_REGISTRATION_VIEW'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'COURSE_REGISTER'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'COURSE_DROP'));
