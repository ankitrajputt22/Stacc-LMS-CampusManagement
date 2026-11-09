-- Study materials of LMS topics. Only the file's details are stored here; the bytes are in private
-- object storage under object_key, a key the application generates. Materials start as drafts and are
-- archived, never deleted, and archiving keeps the stored file too.
CREATE TABLE lms_study_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_topic_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    object_key VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    position INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_study_materials_lms_topic FOREIGN KEY (lms_topic_id) REFERENCES lms_topics (id),
    CONSTRAINT uk_lms_study_materials_object_key UNIQUE (object_key),
    CONSTRAINT ck_lms_study_materials_size CHECK (size_bytes >= 0),
    INDEX idx_lms_study_materials_topic_position (lms_topic_id, position)
);
