# Hackathons

Phase 0.12.12. Hackathons live in the `hackathon` module, at `/hackathons`, with one part per role held.

- The campus office (`ADMIN` with `HACKATHON_MANAGE`) adds a hackathon as a draft (title, description, start, end, and a team size of 1 to 10; `V124`), names active faculty accounts as judges, publishes it, releases the results once after the end, and archives it. Nothing is deleted.
- While a published hackathon has not ended, students (`STUDENT` with `HACKATHON_PARTICIPATE`) form a team (a name unique in the hackathon) or join one with a free place, and may leave. Each student is on at most one team per hackathon (a unique generated column). Joining takes the team row lock and counts current members with a locking read, so a team never passes the size limit (`HackathonMySqlIT`).
- Any member hands in the team's one submission (title, plain-text summary, and at most one `https://` link) and may replace it until the end. The server's clock decides.
- After the end, the named judges (`FACULTY` with `HACKATHON_JUDGE`) score each submission from 0 to 100 with an optional comment, replaced until the results are released. Judges see only their own scores; anyone else gets the same 404.
- Participants never see scores before the release. Then everyone sees each submitted team's total (the sum of the judges' scores) and place (equal totals share a place), worked out on read and never stored. The office sees the standings at any time.

| Route | Who |
| --- | --- |
| `GET /api/v1/hackathons`, `POST …/{id}/teams`, `POST …/teams/{id}/join`, `POST …/{id}/leave`, `POST …/{id}/submission` | `STUDENT` with `HACKATHON_PARTICIPATE` |
| `GET /api/v1/hackathons/judging`, `POST …/submissions/{id}/score` | `FACULTY` with `HACKATHON_JUDGE`, named on the hackathon |
| `GET`/`POST /api/v1/admin/hackathons`, `POST …/{id}/publish`, `…/judges`, `…/release`, `…/archive` | `ADMIN` with `HACKATHON_MANAGE` |
