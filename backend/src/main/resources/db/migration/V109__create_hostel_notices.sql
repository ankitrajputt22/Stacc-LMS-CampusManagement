-- v0.10.9: notices for one hostel or one block of it, written by the hostel office or the hostel's wardens. An
-- emergency notice tells every resident it reaches through the Inbox when it is first published. Plain text; notices
-- are archived, never deleted.
CREATE TABLE hostel_notices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_id BIGINT NOT NULL,
    block VARCHAR(50) NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    emergency BOOLEAN NOT NULL,
    status VARCHAR(10) NOT NULL,
    published_at DATETIME(6) NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_notices_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_hostel_notices_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_hostel_notices_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX ix_hostel_notices_hostel (hostel_id, status, published_at)
);

ALTER TABLE notifications DROP CHECK ck_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED',
    'ASSIGNMENT_GRADED', 'QUIZ_PUBLISHED', 'ANNOUNCEMENT_PUBLISHED', 'ACADEMIC_CALENDAR_PUBLISHED',
    'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED', 'RESULT_PUBLISHED', 'RESULT_UPDATED', 'HELPDESK_REPLY',
    'HELPDESK_STATUS_CHANGED', 'HELPDESK_ASSIGNED', 'APPROVAL_ACTION_REQUIRED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
    'APPROVAL_CANCELLED', 'HOSTEL_ALLOCATED', 'HOSTEL_VACATED', 'HOSTEL_EMERGENCY', 'CAMPUS_NOTICE_PUBLISHED',
    'CAMPUS_SERVICE_READY', 'CAMPUS_SERVICE_COMPLETED'));

-- HOSTEL_NOTICE_MANAGE: the hostel office (any hostel) and wardens (their own hostels) write and publish notices.
INSERT INTO permissions (code) VALUES ('HOSTEL_NOTICE_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_NOTICE_MANAGE' WHERE r.name IN ('ADMIN', 'WARDEN');
