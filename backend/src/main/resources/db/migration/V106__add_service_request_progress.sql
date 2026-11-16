-- v0.9.7: campus service requests show their progress after approval ("being processed", then "ready"), and a
-- request can be completed with an issued document, kept in the requester's document vault with its verification
-- code. The requester is told in the Inbox when a request is ready.
ALTER TABLE campus_service_requests
    ADD COLUMN processing_at DATETIME(6) NULL,
    ADD COLUMN ready_at DATETIME(6) NULL,
    ADD COLUMN issued_document_id BIGINT NULL,
    ADD CONSTRAINT fk_campus_service_requests_document FOREIGN KEY (issued_document_id) REFERENCES vault_documents (id),
    ADD CONSTRAINT uq_campus_service_requests_document UNIQUE (issued_document_id);

ALTER TABLE campus_service_requests DROP CHECK ck_campus_service_requests_status;

ALTER TABLE campus_service_requests ADD CONSTRAINT ck_campus_service_requests_status CHECK (
    status IN ('SUBMITTED', 'APPROVED', 'IN_PROGRESS', 'READY', 'REJECTED', 'CANCELLED', 'COMPLETED'));

ALTER TABLE notifications DROP CHECK ck_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED',
    'ASSIGNMENT_GRADED', 'QUIZ_PUBLISHED', 'ANNOUNCEMENT_PUBLISHED', 'ACADEMIC_CALENDAR_PUBLISHED',
    'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED', 'RESULT_PUBLISHED', 'RESULT_UPDATED', 'HELPDESK_REPLY',
    'HELPDESK_STATUS_CHANGED', 'HELPDESK_ASSIGNED', 'APPROVAL_ACTION_REQUIRED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
    'APPROVAL_CANCELLED', 'HOSTEL_ALLOCATED', 'HOSTEL_VACATED', 'CAMPUS_NOTICE_PUBLISHED', 'CAMPUS_SERVICE_READY',
    'CAMPUS_SERVICE_COMPLETED'));
