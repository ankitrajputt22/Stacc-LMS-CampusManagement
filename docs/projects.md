# Projects

Phase 0.12.10. Projects live in the `projects` module and use one team model for every kind of student project.

- A student (`STUDENT` with `PROJECT_USE`, at `/projects`) starts a project with a title, a plain-text summary, and a member limit of 1 to 20 that includes them (`V122`). They lead it. The lead changes the details (the limit never below the current members), stops or resumes taking members, and archives the project. Nothing is deleted.
- Other students ask to join an open project, once while the request or place is live (a unique generated column), with an optional message only the lead reads. The lead accepts or declines once. Accepting takes the project row lock, then counts active members with a locking read, so a project never passes its limit (`ProjectCapacityMySqlIT`). Members withdraw a request or leave; the lead stays. Every place keeps its history.
- The lead invites active faculty accounts as mentors. The invited faculty member (`FACULTY` with `PROJECT_USE`) accepts or declines once, and an accepted mentor may step down later.
- Students and faculty see every project that is not archived, with members and mentors by login ID only. Join requests are shown only to the lead and to the person who asked.

| Route | Who |
| --- | --- |
| `GET /api/v1/projects` | `STUDENT` or `FACULTY` with `PROJECT_USE` |
| `POST /api/v1/projects`, `PUT …/{id}`, `POST …/{id}/status`, `POST …/{id}/join`, `POST …/members/{id}/decision`, `POST …/members/{id}/leave`, `POST …/{id}/mentors` | `STUDENT` with `PROJECT_USE`; the lead or the member comes from the token |
| `POST /api/v1/projects/mentors/{id}/answer`, `POST …/mentors/{id}/step-down` | `FACULTY` with `PROJECT_USE`, and the invited account |
