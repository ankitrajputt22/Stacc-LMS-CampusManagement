-- v0.13.5: teacher-only documents. Faculty of a course keep plain-text notes and drafts that only Teacher AI reads
-- (assessment drafts); students never see them and student AI never searches them. They are archived, never deleted.
CREATE TABLE ai_teacher_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_teacher_documents_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_teacher_documents_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_ai_teacher_documents_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    INDEX ix_ai_teacher_documents_course_status (lms_course_id, status)
);

-- A course now has one metadata row per index kind: its published content, and its teacher-only documents. The new
-- unique key is added before the old one is dropped, so the foreign key on lms_course_id always has an index.
ALTER TABLE ai_course_knowledge_indexes
    ADD COLUMN index_kind VARCHAR(30) NOT NULL DEFAULT 'COURSE_CONTENT' AFTER lms_course_id,
    ADD CONSTRAINT uk_ai_course_knowledge_indexes_course_kind UNIQUE (lms_course_id, index_kind),
    ADD CONSTRAINT ck_ai_course_knowledge_indexes_kind CHECK (index_kind IN ('COURSE_CONTENT', 'TEACHER_DOCUMENTS'));
ALTER TABLE ai_course_knowledge_indexes DROP INDEX uk_ai_course_knowledge_indexes_lms_course;
