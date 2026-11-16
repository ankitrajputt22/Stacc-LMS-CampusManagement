-- v0.8.5: files on course announcements, and an Inbox notice when an announcement is first published. Files live in
-- private storage like every other LMS file; a removed file is kept, with its bytes, but no longer listed or
-- downloaded. Nothing is deleted.
CREATE TABLE lms_announcement_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    announcement_id BIGINT NOT NULL,
    object_key VARCHAR(300) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    removed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_announcement_attachments_key UNIQUE (object_key),
    CONSTRAINT fk_lms_announcement_attachments_announcement FOREIGN KEY (announcement_id) REFERENCES lms_announcements (id),
    CONSTRAINT ck_lms_announcement_attachments_size CHECK (size_bytes > 0),
    INDEX ix_lms_announcement_attachments_announcement (announcement_id, removed_at)
);

ALTER TABLE notifications DROP CHECK ck_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED',
    'ASSIGNMENT_GRADED', 'QUIZ_PUBLISHED', 'ANNOUNCEMENT_PUBLISHED', 'ACADEMIC_CALENDAR_PUBLISHED',
    'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED', 'RESULT_PUBLISHED', 'RESULT_UPDATED', 'HELPDESK_REPLY',
    'HELPDESK_STATUS_CHANGED', 'HELPDESK_ASSIGNED', 'APPROVAL_ACTION_REQUIRED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
    'APPROVAL_CANCELLED', 'HOSTEL_ALLOCATED', 'HOSTEL_VACATED', 'CAMPUS_NOTICE_PUBLISHED', 'CAMPUS_SERVICE_COMPLETED'));
