-- v0.14.5: model answers and rubric suggestions are logged like other AI requests (kind, course, outcome, duration only).
ALTER TABLE ai_request_log DROP CHECK ck_ai_request_log_kind;
ALTER TABLE ai_request_log ADD CONSTRAINT ck_ai_request_log_kind
    CHECK (kind IN ('ASK', 'SUMMARY', 'PRACTICE', 'ASSESSMENT_DRAFT', 'QUESTION_BANK', 'MODEL_ANSWER', 'RUBRIC'));
