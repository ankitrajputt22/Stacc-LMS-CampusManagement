-- Lets an account read the shared calendar: the official events that apply to it and, for students and faculty,
-- their assignment deadlines. It does not decide which ones: enrollments, teaching assignments, and LMS access do.
INSERT INTO permissions (code) VALUES ('ACADEMIC_CALENDAR_VIEW');

-- Lets an admin create, edit, publish, and cancel official calendar events. Nothing can delete them.
INSERT INTO permissions (code) VALUES ('ACADEMIC_CALENDAR_MANAGE');

-- ACADEMIC_CALENDAR_VIEW goes to STUDENT, FACULTY, and ADMIN; ACADEMIC_CALENDAR_MANAGE to ADMIN only. Every row is
-- looked up by name, never by ID, and a missing role makes a statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_CALENDAR_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_CALENDAR_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_CALENDAR_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_CALENDAR_MANAGE')
);
