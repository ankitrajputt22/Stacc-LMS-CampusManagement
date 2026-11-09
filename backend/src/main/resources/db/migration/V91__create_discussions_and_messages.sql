-- v0.8.2 and v0.8.4: course discussions and private messages between a student and the course's faculty.
-- Threads, replies, conversations, and messages are append-only: nothing is edited or deleted. The author is the
-- account from the access token; author_kind says whether it wrote as a student or as the course's faculty.
CREATE TABLE lms_discussion_threads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    author_user_account_id BIGINT NOT NULL,
    author_kind VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    accepted_reply_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_discussion_threads_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_discussion_threads_author FOREIGN KEY (author_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_discussion_threads_kind CHECK (author_kind IN ('STUDENT', 'FACULTY')),
    INDEX ix_lms_discussion_threads_course (lms_course_id, pinned, last_activity_at)
);

CREATE TABLE lms_discussion_replies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    author_user_account_id BIGINT NOT NULL,
    author_kind VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_discussion_replies_thread FOREIGN KEY (thread_id) REFERENCES lms_discussion_threads (id),
    CONSTRAINT fk_lms_discussion_replies_author FOREIGN KEY (author_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_discussion_replies_kind CHECK (author_kind IN ('STUDENT', 'FACULTY'))
);

ALTER TABLE lms_discussion_threads
    ADD CONSTRAINT fk_lms_discussion_threads_accepted FOREIGN KEY (accepted_reply_id) REFERENCES lms_discussion_replies (id);

CREATE TABLE lms_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    student_user_account_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_message_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_conversations_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_conversations_student FOREIGN KEY (student_user_account_id) REFERENCES user_accounts (id),
    INDEX ix_lms_conversations_course (lms_course_id, last_message_at),
    INDEX ix_lms_conversations_student (student_user_account_id, lms_course_id)
);

CREATE TABLE lms_conversation_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    author_user_account_id BIGINT NOT NULL,
    author_kind VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_conversation_messages_conversation FOREIGN KEY (conversation_id) REFERENCES lms_conversations (id),
    CONSTRAINT fk_lms_conversation_messages_author FOREIGN KEY (author_user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_conversation_messages_kind CHECK (author_kind IN ('STUDENT', 'FACULTY'))
);

-- LMS_DISCUSSION_USE: read and post in the discussions of a course the account may open (as a student or as its
-- faculty). LMS_MESSAGE_USE: private messages between a student and the course's faculty. Roles by name.
INSERT INTO permissions (code) VALUES ('LMS_DISCUSSION_USE'), ('LMS_MESSAGE_USE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('LMS_DISCUSSION_USE', 'LMS_MESSAGE_USE')
WHERE r.name IN ('STUDENT', 'FACULTY');

-- v0.8.1: files on messages, kept in private storage under server-made keys; downloaded only through the conversation.
CREATE TABLE lms_conversation_message_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    object_key VARCHAR(300) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_message_attachments_key UNIQUE (object_key),
    CONSTRAINT fk_lms_message_attachments_message FOREIGN KEY (message_id) REFERENCES lms_conversation_messages (id),
    CONSTRAINT ck_lms_message_attachments_size CHECK (size_bytes > 0)
);
