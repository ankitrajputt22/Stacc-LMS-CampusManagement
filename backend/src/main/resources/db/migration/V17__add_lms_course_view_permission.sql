-- The first real permission. It lets a student account use the LMS course-view feature.
-- It does not decide which courses a student sees: official enrollment and LMS membership do.
INSERT INTO permissions (code) VALUES ('LMS_COURSE_VIEW');

-- Given to the STUDENT role only. Both rows are looked up by name, never by ID, and a missing
-- role makes this statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'STUDENT'),
    (SELECT id FROM permissions WHERE code = 'LMS_COURSE_VIEW')
);
