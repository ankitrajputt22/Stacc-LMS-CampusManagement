-- Lets an admin account read the academic masters: departments, programs, academic sessions, semesters, and
-- courses.
INSERT INTO permissions (code) VALUES ('ACADEMIC_MASTER_VIEW');

-- Lets an admin account create and edit those masters. Nothing can delete them.
INSERT INTO permissions (code) VALUES ('ACADEMIC_MASTER_MANAGE');

-- Both go to ADMIN only; STUDENT and FACULTY get neither. Every row is looked up by name, never by ID, and a
-- missing role makes a statement fail instead of quietly assigning nothing.
INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_MASTER_VIEW')
);

INSERT INTO role_permissions (role_id, permission_id)
VALUES (
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM permissions WHERE code = 'ACADEMIC_MASTER_MANAGE')
);
