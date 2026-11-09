-- v0.2.8: staff roles the college can give accounts. Each gets only what every account needs (its own profile); the
-- permissions of their own work are added with those features. An account may hold a staff role together with others.
INSERT INTO roles (name) VALUES ('HOD'), ('WARDEN'), ('SECURITY'), ('MAINTENANCE'), ('LIBRARIAN'), ('PLACEMENT_OFFICER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('PERSON_PROFILE_VIEW_OWN', 'PERSON_PROFILE_EDIT_OWN')
WHERE role.name IN ('HOD', 'WARDEN', 'SECURITY', 'MAINTENANCE', 'LIBRARIAN', 'PLACEMENT_OFFICER');
