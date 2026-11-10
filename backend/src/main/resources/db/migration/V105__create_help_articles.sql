-- v0.9.6: short self-help articles per helpdesk category, written by the support admins and suggested to requesters
-- while they write a ticket. Plain text. Articles are archived, never deleted.
CREATE TABLE helpdesk_articles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(10) NOT NULL,
    published_at DATETIME(6) NULL,
    updated_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_helpdesk_articles_category FOREIGN KEY (category_id) REFERENCES helpdesk_categories (id),
    CONSTRAINT fk_helpdesk_articles_updated_by FOREIGN KEY (updated_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_helpdesk_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX ix_helpdesk_articles_category (category_id, status)
);
