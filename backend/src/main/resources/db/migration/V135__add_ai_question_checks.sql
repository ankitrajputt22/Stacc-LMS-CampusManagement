-- v0.14.2: quality checks on AI drafts, worked out once when the question was drafted and kept for the reviewer: a
-- possible duplicate in the bank, whether that match is in a final exam paper, whether the question may be outside the
-- course content, and the model's difficulty and Bloom level as suggestions only (the question's own values decide).
ALTER TABLE lms_questions
    ADD COLUMN ai_duplicate_of_question_id BIGINT NULL AFTER ai_generated,
    ADD COLUMN ai_in_previous_paper BOOLEAN NOT NULL DEFAULT FALSE AFTER ai_duplicate_of_question_id,
    ADD COLUMN ai_out_of_syllabus BOOLEAN NOT NULL DEFAULT FALSE AFTER ai_in_previous_paper,
    ADD COLUMN ai_suggested_difficulty VARCHAR(10) NULL AFTER ai_out_of_syllabus,
    ADD COLUMN ai_suggested_bloom_level VARCHAR(20) NULL AFTER ai_suggested_difficulty,
    ADD CONSTRAINT fk_lms_questions_ai_duplicate_of FOREIGN KEY (ai_duplicate_of_question_id) REFERENCES lms_questions (id);
