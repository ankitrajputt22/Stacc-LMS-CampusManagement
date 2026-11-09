# Load tests

These k6 scripts load-test Stacc's busiest reads and its locked writes, against a **throwaway** stack with the
`dev-seed` demo data. Never run them against the real `stacc` project or a college's database.

## Running

1. Start a throwaway stack (for example `e2e/stack.sh`, or a local backend with `SPRING_PROFILES_ACTIVE=dev-seed`).
2. Install k6 (https://k6.io/docs/get-started/installation/).
3. Run a script with the backend's address and the demo password:

   ```bash
   BASE_URL=http://localhost:8080 STACC_TEST_PASSWORD=... k6 run load/reads.js
   ```

| Script | What it does | Targets |
| --- | --- | --- |
| `reads.js` | 50 students sign in, then open the dashboard, courses, To-Do, calendar, and Inbox for 2 minutes. | p95 under 800 ms, under 1% errors |
| `quiz-burst.js` | Many students start and save a practice quiz at once (locked writes). | no 5xx; every start gets one attempt |
| `course-registration-burst.js` | 30 registrations for one course offering at once, 10 from each demo student (`COURSE_CODE`, default `CS305`; `STUDENTS`; `REQUESTS_PER_STUDENT`). Runs in CI on the end-to-end stack. | only 201 or 409; at most one 201 and one enrollment per student; every eligible student gets a place up to the free places; free places go down by exactly the new enrollments; p95 under 3 s |
| `event-registration-burst.js` | Students register for the same event at once (capacity under a row lock). | no 5xx; capacity never exceeded |

## Baseline

k6 is not installed on the development machine this was written on, and no baseline has been recorded yet. Record the
first run's summary (p50, p95, p99, error rate per script, machine, and data size) in the table below before the
release candidate.

| Date | Script | Machine | p95 | Errors | Notes |
| --- | --- | --- | --- | --- | --- |
| (to record) | | | | | |
