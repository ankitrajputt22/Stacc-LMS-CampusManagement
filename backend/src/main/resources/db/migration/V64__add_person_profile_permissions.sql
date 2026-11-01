-- Reading and editing one's own person profile. Every role gets both: a profile belongs to an account, not to a role.
-- Only the preferred name and the phone number can be edited by the person; the college sets the rest. Roles and
-- permissions are looked up by name.
INSERT INTO permissions (code) VALUES ('PERSON_PROFILE_VIEW_OWN'), ('PERSON_PROFILE_EDIT_OWN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('PERSON_PROFILE_VIEW_OWN', 'PERSON_PROFILE_EDIT_OWN')
WHERE role.name IN ('STUDENT', 'FACULTY', 'ADMIN');
