-- College-issued Stacc accounts. Only a password hash is stored, never a plain password.
CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_login_id UNIQUE (login_id)
);
