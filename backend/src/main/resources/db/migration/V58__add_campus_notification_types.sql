-- A campus notice's first publication and a completed campus service request are told in the Inbox. The type check
-- from V54 is replaced by one that also allows the two campus types; existing rows are unchanged.
ALTER TABLE notifications DROP CHECK ck_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED',
    'ASSIGNMENT_GRADED', 'ACADEMIC_CALENDAR_PUBLISHED', 'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED',
    'RESULT_PUBLISHED', 'RESULT_UPDATED', 'HELPDESK_REPLY', 'HELPDESK_STATUS_CHANGED', 'HELPDESK_ASSIGNED',
    'APPROVAL_ACTION_REQUIRED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED', 'APPROVAL_CANCELLED', 'HOSTEL_ALLOCATED',
    'HOSTEL_VACATED', 'CAMPUS_NOTICE_PUBLISHED', 'CAMPUS_SERVICE_COMPLETED'));
