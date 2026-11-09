-- v0.9.4: a workflow step may name a relationship to the requester (such as the student's head of department) in
-- addition to its role. When a request starts, the relationship is resolved once to one account, copied onto the
-- request's step; only that account (still holding the step's role) decides it. Steps without one work as before.
ALTER TABLE approval_workflow_step_definitions ADD COLUMN approver_relationship VARCHAR(40) NULL;
ALTER TABLE approval_request_steps ADD COLUMN approver_relationship VARCHAR(40) NULL;
ALTER TABLE approval_request_steps ADD COLUMN assigned_approver_user_account_id BIGINT NULL;
ALTER TABLE approval_request_steps
    ADD CONSTRAINT fk_approval_request_steps_assigned FOREIGN KEY (assigned_approver_user_account_id) REFERENCES user_accounts (id);
CREATE INDEX ix_approval_request_steps_assigned ON approval_request_steps (assigned_approver_user_account_id, status);

-- Heads of department now decide the steps assigned to them, so HOD gets the approval task permissions. Roles and
-- permissions by name.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('APPROVAL_TASK_VIEW', 'APPROVAL_TASK_DECIDE')
WHERE r.name = 'HOD';
