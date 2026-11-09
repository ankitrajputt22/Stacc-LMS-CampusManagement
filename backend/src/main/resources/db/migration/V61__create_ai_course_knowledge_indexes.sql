-- AI knowledge metadata: one row per LMS course whose AI knowledge has ever been refreshed. No row means the course
-- has not been indexed. The status is that of the latest refresh (BUILDING, READY, or FAILED); whether the knowledge
-- is stale is computed by comparing content_fingerprint, the SHA-256 of the published content last indexed
-- successfully, with the course's current one. indexed_at is when that successful index went live. last_error is a
-- short, fixed, client-safe message, never a stack trace or model output.
-- Only metadata lives here: the vectors and extracted text stay in the AI service's own storage, and no question,
-- answer, or conversation is stored anywhere. Nothing cascades.
CREATE TABLE ai_course_knowledge_indexes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    content_fingerprint CHAR(64) NULL,
    indexed_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_course_knowledge_indexes_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT uk_ai_course_knowledge_indexes_lms_course UNIQUE (lms_course_id),
    CONSTRAINT ck_ai_course_knowledge_indexes_status CHECK (status IN ('BUILDING', 'READY', 'FAILED'))
);
