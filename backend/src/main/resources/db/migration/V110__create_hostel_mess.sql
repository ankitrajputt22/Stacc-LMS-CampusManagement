-- v0.10.10: the hostel mess. A weekly menu per hostel (one line of items per day and meal), changes for single days
-- that replace the weekly line on that date, and residents' feedback on meals they could have eaten: a rating from 1
-- to 5 and an optional comment, once per meal. The hostel office and the hostel's wardens manage the menu and read the
-- feedback, never who gave it.
CREATE TABLE hostel_mess_menu_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_id BIGINT NOT NULL,
    day_of_week INT NOT NULL,
    meal VARCHAR(10) NOT NULL,
    items VARCHAR(500) NOT NULL,
    updated_by_account_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_mess_menu_items_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_hostel_mess_menu_items_updated_by FOREIGN KEY (updated_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hostel_mess_menu_items_slot UNIQUE (hostel_id, day_of_week, meal),
    CONSTRAINT ck_hostel_mess_menu_items_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_hostel_mess_menu_items_meal CHECK (meal IN ('BREAKFAST', 'LUNCH', 'SNACKS', 'DINNER'))
);

CREATE TABLE hostel_mess_menu_changes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_id BIGINT NOT NULL,
    menu_date DATE NOT NULL,
    meal VARCHAR(10) NOT NULL,
    items VARCHAR(500) NOT NULL,
    note VARCHAR(200) NULL,
    updated_by_account_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_mess_menu_changes_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_hostel_mess_menu_changes_updated_by FOREIGN KEY (updated_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hostel_mess_menu_changes_slot UNIQUE (hostel_id, menu_date, meal),
    CONSTRAINT ck_hostel_mess_menu_changes_meal CHECK (meal IN ('BREAKFAST', 'LUNCH', 'SNACKS', 'DINNER'))
);

CREATE TABLE hostel_meal_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hostel_id BIGINT NOT NULL,
    student_account_id BIGINT NOT NULL,
    meal_date DATE NOT NULL,
    meal VARCHAR(10) NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hostel_meal_feedback_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_hostel_meal_feedback_student FOREIGN KEY (student_account_id) REFERENCES user_accounts (id),
    CONSTRAINT uq_hostel_meal_feedback_once UNIQUE (student_account_id, meal_date, meal),
    CONSTRAINT ck_hostel_meal_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_hostel_meal_feedback_meal CHECK (meal IN ('BREAKFAST', 'LUNCH', 'SNACKS', 'DINNER')),
    INDEX ix_hostel_meal_feedback_hostel (hostel_id, meal_date)
);

-- HOSTEL_MESS_MANAGE: the hostel office (every hostel) and wardens (their own) manage menus and read meal feedback.
INSERT INTO permissions (code) VALUES ('HOSTEL_MESS_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HOSTEL_MESS_MANAGE' WHERE r.name IN ('ADMIN', 'WARDEN');
