-- v0.11.2: club and sports group sessions (meetings and practices, on members' calendars) and group announcements
-- (told to members through the Inbox). Written by the group's officers or the campus office; sessions are cancelled
-- and announcements kept, never deleted.
CREATE TABLE engagement_group_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    kind VARCHAR(10) NOT NULL,
    title VARCHAR(200) NOT NULL,
    location VARCHAR(200) NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_engagement_group_sessions_group FOREIGN KEY (group_id) REFERENCES campus_groups (id),
    CONSTRAINT fk_engagement_group_sessions_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_engagement_group_sessions_kind CHECK (kind IN ('MEETING', 'PRACTICE')),
    CONSTRAINT ck_engagement_group_sessions_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT ck_engagement_group_sessions_order CHECK (starts_at < ends_at),
    INDEX ix_engagement_group_sessions_group (group_id, starts_at)
);

CREATE TABLE engagement_group_announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_engagement_group_announcements_group FOREIGN KEY (group_id) REFERENCES campus_groups (id),
    CONSTRAINT fk_engagement_group_announcements_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    INDEX ix_engagement_group_announcements_group (group_id, created_at)
);

ALTER TABLE notifications DROP CHECK ck_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED',
    'ASSIGNMENT_GRADED', 'QUIZ_PUBLISHED', 'ANNOUNCEMENT_PUBLISHED', 'ACADEMIC_CALENDAR_PUBLISHED',
    'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED', 'RESULT_PUBLISHED', 'RESULT_UPDATED', 'HELPDESK_REPLY',
    'HELPDESK_STATUS_CHANGED', 'HELPDESK_ASSIGNED', 'APPROVAL_ACTION_REQUIRED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
    'APPROVAL_CANCELLED', 'HOSTEL_ALLOCATED', 'HOSTEL_VACATED', 'HOSTEL_EMERGENCY', 'CAMPUS_NOTICE_PUBLISHED',
    'CAMPUS_SERVICE_READY', 'CAMPUS_SERVICE_COMPLETED', 'GROUP_ANNOUNCEMENT'));
