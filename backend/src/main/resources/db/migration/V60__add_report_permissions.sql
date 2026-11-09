-- Reports. FACULTY_REPORT_VIEW lets faculty see counts for the course offerings they actively teach; ADMIN_REPORT_VIEW
-- lets admins see academic and enrollment counts. Neither is given to STUDENT, and neither replaces a module's own
-- permission: admin operational counts still need HELPDESK_TICKET_VIEW_ALL, HOSTEL_MANAGE, or CAMPUS_SERVICE_MANAGE.
-- Reports store nothing, so there is no table. Roles are looked up by name.
INSERT INTO permissions (code) VALUES ('FACULTY_REPORT_VIEW'), ('ADMIN_REPORT_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'FACULTY_REPORT_VIEW'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'ADMIN_REPORT_VIEW'));
