-- AI learning features. AI_STUDY_USE lets students use the course AI study assistant (questions, topic summaries,
-- practice questions) in courses they may already open; AI_TEACHING_USE lets faculty see and refresh a course's AI
-- knowledge and generate assessment drafts for courses they already teach. Neither replaces the LMS permissions or
-- access policies, and ADMIN gets neither. Roles are looked up by name.
INSERT INTO permissions (code) VALUES ('AI_STUDY_USE'), ('AI_TEACHING_USE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'AI_STUDY_USE'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'AI_TEACHING_USE'));
