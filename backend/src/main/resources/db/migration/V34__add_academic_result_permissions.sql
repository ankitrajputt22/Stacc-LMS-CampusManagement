-- Lets a faculty account record, publish, and correct official course results. It does not decide for which
-- course offerings: official teaching assignments do.
INSERT INTO permissions (code) VALUES ('ACADEMIC_RESULT_MANAGE');

-- Lets a student account read its own published course results. Which results is decided by the student's own
-- course enrollments.
INSERT INTO permissions (code) VALUES ('ACADEMIC_RESULT_VIEW');

-- ACADEMIC_RESULT_MANAGE goes to FACULTY and ACADEMIC_RESULT_VIEW to STUDENT only; ADMIN gets neither. Every row
-- is looked up by name, never by ID, and a missing role makes a statement fail instead of quietly assigning
-- nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'FACULTY'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_RESULT_MANAGE')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_RESULT_VIEW')
);
