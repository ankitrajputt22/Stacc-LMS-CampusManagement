-- v0.12.13: the library. Librarians keep the catalog (titles and their copies, by barcode), lend and take back copies,
-- and list digital resources. One open loan per copy and at most five per borrower; holds queue for a title and take
-- precedence over lending to others. Overdue is worked out from the due time on read. Nothing is deleted: titles and
-- resources are made inactive, copies withdrawn, holds cancelled, loans returned.
CREATE TABLE library_titles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(300) NOT NULL,
    authors VARCHAR(300) NULL,
    isbn VARCHAR(20) NULL,
    publisher VARCHAR(200) NULL,
    published_year INT NULL,
    subject VARCHAR(150) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_library_titles PRIMARY KEY (id),
    CONSTRAINT uq_library_titles_isbn UNIQUE (isbn),
    INDEX ix_library_titles_title (title)
);

CREATE TABLE library_copies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title_id BIGINT NOT NULL,
    barcode VARCHAR(40) NOT NULL,
    shelf VARCHAR(50) NULL,
    withdrawn_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_library_copies PRIMARY KEY (id),
    CONSTRAINT fk_library_copies_title FOREIGN KEY (title_id) REFERENCES library_titles (id),
    CONSTRAINT uq_library_copies_barcode UNIQUE (barcode)
);

CREATE TABLE library_loans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    copy_id BIGINT NOT NULL,
    borrower_account_id BIGINT NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    renewed BOOLEAN NOT NULL DEFAULT FALSE,
    returned_at DATETIME(6) NULL,
    issued_by_account_id BIGINT NOT NULL,
    live_copy BIGINT AS (CASE WHEN returned_at IS NULL THEN copy_id END) STORED,
    CONSTRAINT pk_library_loans PRIMARY KEY (id),
    CONSTRAINT fk_library_loans_copy FOREIGN KEY (copy_id) REFERENCES library_copies (id),
    CONSTRAINT fk_library_loans_borrower FOREIGN KEY (borrower_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_library_loans_issued_by FOREIGN KEY (issued_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_library_loans_live_copy UNIQUE (live_copy),
    INDEX ix_library_loans_borrower (borrower_account_id, returned_at)
);

CREATE TABLE library_holds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    placed_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN status = 'ACTIVE' THEN CONCAT(title_id, ':', account_id) END) STORED,
    CONSTRAINT pk_library_holds PRIMARY KEY (id),
    CONSTRAINT fk_library_holds_title FOREIGN KEY (title_id) REFERENCES library_titles (id),
    CONSTRAINT fk_library_holds_account FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_library_holds_status CHECK (status IN ('ACTIVE', 'FULFILLED', 'CANCELLED')),
    CONSTRAINT uq_library_holds_live UNIQUE (live_key),
    INDEX ix_library_holds_title (title_id, status, placed_at)
);

CREATE TABLE library_resources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    url VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_library_resources PRIMARY KEY (id)
);

INSERT INTO permissions (code) VALUES ('LIBRARY_USE'), ('LIBRARY_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'LIBRARY_USE' WHERE r.name IN ('STUDENT', 'FACULTY');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'LIBRARY_MANAGE' WHERE r.name = 'LIBRARIAN';
