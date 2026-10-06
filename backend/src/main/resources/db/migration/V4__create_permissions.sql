-- Actions that can later be granted to roles. Rows are added by later migrations when real features need them.
CREATE TABLE permissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permissions_code UNIQUE (code)
);
