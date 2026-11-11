-- v0.12.9: portfolios. A student's portfolio shows records read live from their sources (never copied here) and items
-- the student enters. A self-entered item may be sent to one named faculty member, who verifies or declines it once;
-- a verified item is no longer edited. Items are hidden by their owner, never deleted.
CREATE TABLE portfolio_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_account_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    occurred_on DATE NULL,
    link VARCHAR(500) NULL,
    verification VARCHAR(20) NOT NULL,
    verifier_account_id BIGINT NULL,
    verification_note VARCHAR(1000) NULL,
    requested_at DATETIME(6) NULL,
    decided_at DATETIME(6) NULL,
    hidden_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_portfolio_items PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_items_owner FOREIGN KEY (owner_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_portfolio_items_verifier FOREIGN KEY (verifier_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_portfolio_items_kind CHECK (kind IN ('ACHIEVEMENT', 'PROJECT', 'COURSE', 'SKILL', 'SERVICE', 'OTHER')),
    CONSTRAINT ck_portfolio_items_verification CHECK (verification IN ('NONE', 'REQUESTED', 'VERIFIED', 'DECLINED')),
    INDEX ix_portfolio_items_owner (owner_account_id),
    INDEX ix_portfolio_items_verifier (verifier_account_id, verification)
);

INSERT INTO permissions (code) VALUES ('PORTFOLIO_USE'), ('PORTFOLIO_VERIFY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'PORTFOLIO_USE' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'PORTFOLIO_VERIFY' WHERE r.name = 'FACULTY';
