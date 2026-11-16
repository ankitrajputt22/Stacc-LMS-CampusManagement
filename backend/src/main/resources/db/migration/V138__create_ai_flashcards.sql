-- v0.15.3: a student's flashcard sets, made by AI from published course content when the student asks, and reviewed with
-- spaced repetition. Owned by the student who made them; deleting a set removes it and its cards for good.
CREATE TABLE ai_flashcard_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_flashcard_sets_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_flashcard_sets_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    INDEX ix_ai_flashcard_sets_account_course (user_account_id, lms_course_id)
);

-- Each card keeps the source it came from (a snapshot of its title) and its own review state; the next due day is
-- worked out by code from the student's rating.
CREATE TABLE ai_flashcards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ai_flashcard_set_id BIGINT NOT NULL,
    position INT NOT NULL,
    front VARCHAR(300) NOT NULL,
    back VARCHAR(1000) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    source_title VARCHAR(200) NOT NULL,
    page_id BIGINT NULL,
    ease DECIMAL(4,2) NOT NULL,
    interval_days INT NOT NULL,
    repetitions INT NOT NULL,
    lapses INT NOT NULL,
    due_on DATE NOT NULL,
    last_reviewed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_flashcards_set FOREIGN KEY (ai_flashcard_set_id) REFERENCES ai_flashcard_sets (id) ON DELETE CASCADE,
    CONSTRAINT uk_ai_flashcards_set_position UNIQUE (ai_flashcard_set_id, position),
    INDEX ix_ai_flashcards_due (ai_flashcard_set_id, due_on)
);

ALTER TABLE ai_request_log DROP CHECK ck_ai_request_log_kind;
ALTER TABLE ai_request_log ADD CONSTRAINT ck_ai_request_log_kind
    CHECK (kind IN ('ASK', 'SUMMARY', 'PRACTICE', 'ASSESSMENT_DRAFT', 'QUESTION_BANK', 'MODEL_ANSWER', 'RUBRIC', 'FLASHCARDS'));
