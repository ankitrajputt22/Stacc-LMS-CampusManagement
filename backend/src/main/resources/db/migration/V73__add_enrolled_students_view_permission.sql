-- v0.4.3: faculty see the students registered in the course offerings they teach.
INSERT INTO permissions (code) VALUES ('ENROLLED_STUDENTS_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'ENROLLED_STUDENTS_VIEW'));
