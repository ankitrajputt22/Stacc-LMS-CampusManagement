-- The approval workflow hostel applications use: one step, decided by ADMIN. It is product reference data, created
-- only when no workflow with this code exists yet, and its step only when the workflow has none, so the migration never
-- changes a workflow an admin has edited. The role is looked up by name. Editing the workflow later through the approval
-- admin API changes future applications only: running ones keep the steps they copied.
INSERT INTO approval_workflow_definitions (code, name, description, active, created_at, updated_at)
SELECT 'HOSTEL_APPLICATION', 'Hostel Application Approval',
       'Approval of a student''s hostel application for an academic session.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_workflow_definitions WHERE code = 'HOSTEL_APPLICATION');

INSERT INTO approval_workflow_step_definitions (workflow_definition_id, step_order, name, approver_role_id, created_at,
    updated_at)
SELECT workflow.id, 1, 'Administrative Approval', admin_role.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM approval_workflow_definitions workflow
JOIN roles admin_role ON admin_role.name = 'ADMIN'
WHERE workflow.code = 'HOSTEL_APPLICATION'
  AND NOT EXISTS (SELECT 1 FROM approval_workflow_step_definitions existing WHERE existing.workflow_definition_id = workflow.id);
