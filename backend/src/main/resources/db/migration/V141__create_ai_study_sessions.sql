-- v0.15.7: study sessions a student chose to add to their To-Do list from a study plan. The plan itself is worked out
-- by code on request and never stored; these rows are only what the student asked to keep, and only they see them.
CREATE TABLE ai_study_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    planned_on DATE NOT NULL,
    title VARCHAR(250) NOT NULL,
    minutes INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_study_sessions_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_study_sessions_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_ai_study_sessions_minutes CHECK (minutes > 0 AND minutes <= 600),
    INDEX ix_ai_study_sessions_account_day (user_account_id, planned_on)
);
