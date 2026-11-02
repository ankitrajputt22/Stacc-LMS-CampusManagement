-- v0.6.1: course texts can use Stacc's small formatting set. Each text gets its format beside it; every existing text
-- stays PLAIN, so it keeps showing exactly as it did. The text itself is stored as written, never as HTML.
ALTER TABLE lms_topics
    ADD COLUMN content_format VARCHAR(20) NOT NULL DEFAULT 'PLAIN' AFTER content,
    ADD CONSTRAINT ck_lms_topics_content_format CHECK (content_format IN ('PLAIN', 'FORMATTED'));

ALTER TABLE lms_announcements
    ADD COLUMN message_format VARCHAR(20) NOT NULL DEFAULT 'PLAIN' AFTER message,
    ADD CONSTRAINT ck_lms_announcements_message_format CHECK (message_format IN ('PLAIN', 'FORMATTED'));

ALTER TABLE lms_assignments
    ADD COLUMN instructions_format VARCHAR(20) NOT NULL DEFAULT 'PLAIN' AFTER instructions,
    ADD CONSTRAINT ck_lms_assignments_instructions_format CHECK (instructions_format IN ('PLAIN', 'FORMATTED'));
