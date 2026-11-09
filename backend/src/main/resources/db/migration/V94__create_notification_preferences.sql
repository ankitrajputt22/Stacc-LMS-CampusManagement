-- v0.9.1: each person's choice per notification category: whether it shows in the Inbox, and whether it is emailed
-- at once, in a daily digest, or not at all. No row means the default (Inbox on, no email). Digest entries wait in
-- notification_digest_items until the daily digest sends them; then sent_at is set. Nothing holds an email address.
CREATE TABLE notification_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    in_app BOOLEAN NOT NULL,
    email VARCHAR(20) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_preferences UNIQUE (user_account_id, category),
    CONSTRAINT fk_notification_preferences_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_notification_preferences_category CHECK (category IN ('COURSES', 'CALENDAR', 'SUPPORT', 'APPROVALS', 'CAMPUS')),
    CONSTRAINT ck_notification_preferences_email CHECK (email IN ('OFF', 'INSTANT', 'DAILY'))
);

CREATE TABLE notification_digest_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    notification_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_digest_items UNIQUE (user_account_id, notification_id),
    CONSTRAINT fk_notification_digest_items_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_notification_digest_items_notification FOREIGN KEY (notification_id) REFERENCES notifications (id),
    INDEX ix_notification_digest_items_pending (sent_at, user_account_id)
);
