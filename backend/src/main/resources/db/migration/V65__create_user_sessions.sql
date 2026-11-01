-- One row per sign-in. Every request names its session (the "sid" claim of its token), and only an open session of an
-- active account is accepted, so signing out, signing out everywhere, and disabling an account take effect at once.
-- Rows are kept after they end, as a short history of where an account was signed in; nothing secret is stored.
CREATE TABLE user_sessions (
    id CHAR(36) NOT NULL,
    user_account_id BIGINT NOT NULL,
    device VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    end_reason VARCHAR(30) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    INDEX idx_user_sessions_account_open (user_account_id, ended_at)
);
