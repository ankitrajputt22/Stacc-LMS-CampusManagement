-- Lets a student account hand in work for LMS assignments and read back its own attempts and files. It
-- does not decide which assignments: official course enrollment does, through the student access rule.
INSERT INTO permissions (code) VALUES ('LMS_ASSIGNMENT_SUBMIT');

-- Given to the STUDENT role only. Both rows are looked up by name, never by ID, and a missing
-- role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'LMS_ASSIGNMENT_SUBMIT')
);
