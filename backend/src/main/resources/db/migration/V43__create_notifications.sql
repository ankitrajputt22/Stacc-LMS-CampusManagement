-- In-app notifications. A notification holds the content of one event once: its type, plain-text title and message,
-- a loose reference to the record it is about (no foreign key: it is history, and the source feature keeps its own
-- access rules), and an optional path inside the application. Each account that receives it has one recipient row,
-- unread while read_at is empty. An account receives a notification at most once. Nothing cascades and nothing is
-- deleted.
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    source_type VARCHAR(40) NULL,
    source_id BIGINT NULL,
    action_path VARCHAR(300) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED', 'ASSIGNMENT_GRADED',
        'ACADEMIC_CALENDAR_PUBLISHED', 'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED', 'RESULT_PUBLISHED',
        'RESULT_UPDATED')),
    CONSTRAINT ck_notifications_source CHECK ((source_type IS NULL) = (source_id IS NULL)),
    CONSTRAINT ck_notifications_action_path CHECK (action_path IS NULL OR (action_path LIKE '/%' AND action_path NOT LIKE '//%')),
    INDEX idx_notifications_created_at (created_at)
);

CREATE TABLE notification_recipients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipients_notification FOREIGN KEY (notification_id) REFERENCES notifications (id),
    CONSTRAINT fk_notification_recipients_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_notification_recipients_notification_account UNIQUE (notification_id, user_account_id),
    INDEX idx_notification_recipients_account_read (user_account_id, read_at, created_at),
    INDEX idx_notification_recipients_account_created (user_account_id, created_at)
);
