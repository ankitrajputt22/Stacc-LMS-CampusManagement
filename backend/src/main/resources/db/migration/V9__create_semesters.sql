-- One numbered semester of one programme in one academic session. The three together are unique.
CREATE TABLE semesters (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    academic_session_id BIGINT NOT NULL,
    semester_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_semesters_program FOREIGN KEY (program_id) REFERENCES programs (id),
    CONSTRAINT fk_semesters_academic_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT uk_semesters_program_session_number UNIQUE (program_id, academic_session_id, semester_number),
    CONSTRAINT ck_semesters_semester_number CHECK (semester_number BETWEEN 1 AND 20)
);
