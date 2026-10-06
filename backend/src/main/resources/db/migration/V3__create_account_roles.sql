-- Roles that the college can assign to accounts.
CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

-- Links accounts to roles. An account can hold more than one role.
CREATE TABLE user_account_roles (
    user_account_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_account_id, role_id),
    CONSTRAINT fk_user_account_roles_user_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_user_account_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- The initial official roles. This is reference data, not user data.
INSERT INTO roles (name) VALUES ('STUDENT'), ('FACULTY'), ('ADMIN');
