-- v0.9.2: the To-Do list is worked out live from its sources; only a person's dismissals are stored. A key names one
-- item (such as ASSIGNMENT:12); grading keys carry the waiting count, so new work shows again. Nothing else is kept.
CREATE TABLE todo_dismissals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    item_key VARCHAR(100) NOT NULL,
    dismissed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_todo_dismissals_item UNIQUE (user_account_id, item_key),
    CONSTRAINT fk_todo_dismissals_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

-- TODO_VIEW: reading and dismissing one's own To-Do list. Roles by name.
INSERT INTO permissions (code) VALUES ('TODO_VIEW');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'TODO_VIEW' WHERE r.name IN ('STUDENT', 'FACULTY', 'ADMIN');
