# Innovation

Phase 0.12.11. Ideas live in the `innovation` module, at `/innovation`, with one part per role held.

- Students and faculty (`INNOVATION_SUBMIT`) submit ideas: a title, the problem, and the idea, in plain text (`V123`). They withdraw an idea while it is still under review. Nothing is deleted.
- Faculty reviewers (`FACULTY` with `INNOVATION_REVIEW`) see open ideas (submitted or shortlisted) other than their own. Each gives one score from 1 to 5 and a comment per idea (unique per idea and reviewer), replaced while the idea is open. Reviewers see only their own review.
- The campus office (`ADMIN` with `INNOVATION_MANAGE`) sees every idea with every review and the reviewers' login IDs. It decides with an optional note: a submitted idea may be shortlisted, and a submitted or shortlisted idea accepted or not selected. Decisions move forward only.
- Once an idea is accepted or not selected, its submitter sees the decision note and the reviewers' comments, without names or scores.
- Every change takes the idea row lock, so a decision and a withdrawal never both succeed (`InnovationMySqlIT`).

| Route | Who |
| --- | --- |
| `GET /api/v1/innovation/ideas/me`, `POST /api/v1/innovation/ideas`, `POST …/ideas/{id}/withdraw` | `STUDENT` or `FACULTY` with `INNOVATION_SUBMIT`; the submitter comes from the token |
| `GET /api/v1/innovation/reviews`, `POST …/ideas/{id}/review` | `FACULTY` with `INNOVATION_REVIEW` |
| `GET /api/v1/admin/innovation/ideas`, `POST …/ideas/{id}/decision` | `ADMIN` with `INNOVATION_MANAGE` |
