-- Global search. GLOBAL_SEARCH_USE only lets an account use the search itself: every kind of result is still decided
-- by its own domain's roles and permissions, so search never shows what the account could not open otherwise. Search
-- stores nothing, so there is no table. Roles are looked up by name.
INSERT INTO permissions (code) VALUES ('GLOBAL_SEARCH_USE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'GLOBAL_SEARCH_USE'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'GLOBAL_SEARCH_USE'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'GLOBAL_SEARCH_USE'));
