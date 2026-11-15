-- v0.13.3: faculty may keep a published study material out of the course's AI knowledge. Students still see and
-- download it as before; only the AI index leaves it out. Existing materials stay included.
ALTER TABLE lms_study_materials
    ADD COLUMN excluded_from_ai BOOLEAN NOT NULL DEFAULT FALSE;
