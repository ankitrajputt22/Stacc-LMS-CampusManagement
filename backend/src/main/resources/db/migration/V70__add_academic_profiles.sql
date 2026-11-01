-- v0.3.4: roll numbers, and the permissions for academic profiles: admins view and correct the official mapping of
-- students and faculty; students and faculty view their own.

-- The college's roll number for a student, unique when set. Kept apart from the login ID, which stays the student ID.
ALTER TABLE student_profiles
    ADD COLUMN roll_number VARCHAR(30) NULL AFTER admission_session_id,
    ADD CONSTRAINT uk_student_profiles_roll_number UNIQUE (roll_number);

INSERT INTO permissions (code) VALUES ('ACADEMIC_PROFILE_VIEW'), ('ACADEMIC_PROFILE_MANAGE'), ('ACADEMIC_PROFILE_VIEW_OWN');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'ACADEMIC_PROFILE_VIEW'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'ACADEMIC_PROFILE_MANAGE'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'ACADEMIC_PROFILE_VIEW_OWN'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'ACADEMIC_PROFILE_VIEW_OWN'));
