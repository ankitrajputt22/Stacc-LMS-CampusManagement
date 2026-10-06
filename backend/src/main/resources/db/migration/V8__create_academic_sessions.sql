-- College-wide academic sessions. A session is not tied to a department or a programme.
CREATE TABLE academic_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_sessions_code UNIQUE (code),
    CONSTRAINT ck_academic_sessions_dates CHECK (end_date > start_date)
);
