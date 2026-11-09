-- Approval steps waiting for a decision, and approved, rejected, and cancelled requests, are told in the Inbox. The type
-- check from V47 is replaced by one that also allows the four approval types; existing rows are unchanged.
ALTER TABLE notifications DROP CHECK ck_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED',
    'ASSIGNMENT_GRADED', 'ACADEMIC_CALENDAR_PUBLISHED', 'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED',
    'RESULT_PUBLISHED', 'RESULT_UPDATED', 'HELPDESK_REPLY', 'HELPDESK_STATUS_CHANGED', 'HELPDESK_ASSIGNED',
    'APPROVAL_ACTION_REQUIRED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED', 'APPROVAL_CANCELLED'));
