-- v0.16.4: area analytics. Counts for hostel, helpdesk, campus engagement, placement, library, and AI course indexes,
-- each section shown only to holders of that area's own permission. Nothing is stored.
INSERT INTO permissions (code) VALUES ('AREA_ANALYTICS_VIEW');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'AREA_ANALYTICS_VIEW' WHERE r.name IN ('ADMIN', 'LIBRARIAN', 'PLACEMENT_OFFICER');
