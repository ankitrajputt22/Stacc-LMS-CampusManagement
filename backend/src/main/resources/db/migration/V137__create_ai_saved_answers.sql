-- v0.15.1 (decision 6): AI keeps no conversation history. A student saves only the answers they choose, with the sources
-- the server rebuilt for them, and may delete them for good. Read only by the student who saved them.
CREATE TABLE ai_saved_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    question VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_saved_answers_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_saved_answers_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    INDEX ix_ai_saved_answers_account_course (user_account_id, lms_course_id)
);

CREATE TABLE ai_saved_answer_sources (
    ai_saved_answer_id BIGINT NOT NULL,
    position INT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(255) NULL,
    page_number INT NULL,
    slide_number INT NULL,
    page_id BIGINT NULL,
    PRIMARY KEY (ai_saved_answer_id, position),
    CONSTRAINT fk_ai_saved_answer_sources_answer FOREIGN KEY (ai_saved_answer_id) REFERENCES ai_saved_answers (id) ON DELETE CASCADE
);
