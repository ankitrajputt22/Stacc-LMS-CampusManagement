-- v0.16.3: department analytics. A head of department reads counts and class-sized percentages for the departments
-- they head now (worked out by DepartmentHeadResolver's rule); nothing is stored.
INSERT INTO permissions (code) VALUES ('DEPARTMENT_ANALYTICS_VIEW');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'DEPARTMENT_ANALYTICS_VIEW' WHERE r.name = 'HOD';
