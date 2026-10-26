-- The approval workflow campus services that need approval may use: one step, decided by ADMIN. It is product
-- reference data, created only when no workflow with this code exists yet, and its step only when the workflow has
-- none, so the migration never changes a workflow an admin has edited. The role is looked up by name. A service names
-- its workflow by code, so an admin may also point a service at another workflow.
INSERT INTO approval_workflow_definitions (code, name, description, active, created_at, updated_at)
SELECT 'CAMPUS_SERVICE_REQUEST', 'Campus Service Request Approval',
       'Approval of a campus service request whose service needs approval.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_workflow_definitions WHERE code = 'CAMPUS_SERVICE_REQUEST');

INSERT INTO approval_workflow_step_definitions (workflow_definition_id, step_order, name, approver_role_id, created_at,
    updated_at)
SELECT workflow.id, 1, 'Administrative Approval', admin_role.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM approval_workflow_definitions workflow
JOIN roles admin_role ON admin_role.name = 'ADMIN'
WHERE workflow.code = 'CAMPUS_SERVICE_REQUEST'
  AND NOT EXISTS (SELECT 1 FROM approval_workflow_step_definitions existing WHERE existing.workflow_definition_id = workflow.id);
