-- v0.14.1: AI drafts in the question bank. A question drafted by AI says so and keeps the course sources it was drafted
-- from (a snapshot of their titles and places, for the reviewer). It starts NEEDS_REVIEW like every new question.
ALTER TABLE lms_questions ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE AFTER practice_allowed;

CREATE TABLE lms_question_sources (
    lms_question_id BIGINT NOT NULL,
    position INT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(255) NULL,
    page_number INT NULL,
    slide_number INT NULL,
    page_id BIGINT NULL,
    PRIMARY KEY (lms_question_id, position),
    CONSTRAINT fk_lms_question_sources_question FOREIGN KEY (lms_question_id) REFERENCES lms_questions (id),
    CONSTRAINT ck_lms_question_sources_type CHECK (source_type IN ('TOPIC', 'STUDY_MATERIAL', 'TEACHER_DOCUMENT'))
);

-- AI requests now include question bank drafts.
ALTER TABLE ai_request_log DROP CHECK ck_ai_request_log_kind;
ALTER TABLE ai_request_log ADD CONSTRAINT ck_ai_request_log_kind
    CHECK (kind IN ('ASK', 'SUMMARY', 'PRACTICE', 'ASSESSMENT_DRAFT', 'QUESTION_BANK'));
