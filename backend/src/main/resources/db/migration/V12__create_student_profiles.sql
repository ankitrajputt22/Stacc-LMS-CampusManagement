-- The academic profile of a student account. The student ID is the account's login ID, and the
-- department comes from the programme, so neither is stored here.
CREATE TABLE student_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    admission_session_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_student_profiles_user_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_student_profiles_program FOREIGN KEY (program_id) REFERENCES programs (id),
    CONSTRAINT fk_student_profiles_admission_session FOREIGN KEY (admission_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT uk_student_profiles_user_account UNIQUE (user_account_id)
);
