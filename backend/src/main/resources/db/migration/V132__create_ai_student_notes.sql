-- v0.13.6: a student's private notes in one course, read only by that student and by their own AI Study Assistant
-- when they choose "Course + my notes". Unlike other records, a student deletes a note for real: the row goes, and the
-- notes index is rebuilt without it, or removed when no note is left.
CREATE TABLE ai_student_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_student_notes_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_student_notes_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    INDEX ix_ai_student_notes_account_course (user_account_id, lms_course_id)
);

-- What was last indexed of one student's notes in one course: only the fingerprint and the time, never the text. A
-- missing row or another fingerprint means the notes index is rebuilt before it is used.
CREATE TABLE ai_student_note_indexes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    content_fingerprint CHAR(64) NOT NULL,
    indexed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_student_note_indexes_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_student_note_indexes_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_ai_student_note_indexes_course_account UNIQUE (lms_course_id, user_account_id)
);
