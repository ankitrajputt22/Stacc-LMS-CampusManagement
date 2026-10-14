-- The academic profile of a faculty account: the faculty member's primary department. The
-- employee ID is the account's login ID, so it is not stored here. What a faculty member teaches
-- is not part of the profile.
CREATE TABLE faculty_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_faculty_profiles_user_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_faculty_profiles_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT uk_faculty_profiles_user_account UNIQUE (user_account_id)
);
