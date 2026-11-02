-- v0.2.9: who heads each department, kept as terms. A department has at most one active term (the generated column is
-- NULL for ended terms, so only active ones collide). Terms end; they are never deleted or reopened.
CREATE TABLE department_heads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    active_department_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN department_id END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_department_heads_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_department_heads_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_department_heads_active UNIQUE (active_department_id),
    CONSTRAINT ck_department_heads_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_department_heads_term CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL) OR (status = 'ENDED' AND ended_at IS NOT NULL AND ended_at >= started_at)),
    INDEX ix_department_heads_department (department_id, started_at),
    INDEX ix_department_heads_account (user_account_id)
);

INSERT INTO permissions (code) VALUES ('DEPARTMENT_HEAD_VIEW'), ('DEPARTMENT_HEAD_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'DEPARTMENT_HEAD_VIEW'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'DEPARTMENT_HEAD_MANAGE'));
