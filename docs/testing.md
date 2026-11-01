# Testing

Stacc has several kinds of tests. Each kind answers a different question, and CI runs all of them on every change (see `continuous-integration.md`). New kinds are added by the roadmap's v0.1 phases.

| Kind | Where | Answers | Runs with |
| --- | --- | --- | --- |
| Backend unit and API tests | `backend/src/test`, classes named `*Test` or `*Tests` | Do the business rules, validation, permissions, and API answers work? | `mvn test` |
| Backend MySQL tests | `backend/src/test`, classes named `*MySqlIT` | Do the migrations, constraints, and row locks work on a real MySQL server? | `mvn verify` (needs Docker) |
| Frontend unit tests | `frontend/src`, files named `*.test.ts` or `*.test.tsx` next to the code | Do the frontend's rules (sign-in, roles, navigation, the API client) and shared components behave? | `npm test` |
| AI service tests | `ai-service/tests` | Do extraction, indexing, retrieval, and the internal API work? | `pytest` |
| End-to-end tests | `e2e/tests` | Do real journeys work in a real browser against the whole stack? | `npm test` in `e2e/` (needs Docker) |

## Backend unit and API tests

Most backend tests need no database. Tests that call the API through the whole application extend `DatabaseFreeApiTest`, which replaces every repository with a stand-in. A new service that needs repositories must have them listed there, or those tests cannot start.

```bash
cd backend && mvn test
```

## Backend MySQL tests

Some things can only be shown on a real database: that every Flyway migration applies to an empty MySQL server, that the entities match the tables, and that the row locks keep their promises when requests arrive at the same moment. Those tests extend `MySqlIntegrationTest`:

- **One throwaway MySQL 8.4 container** (the version `compose.yaml` uses) is started by Testcontainers for the whole run and removed afterwards. It never touches your own `stacc` database or Docker project.
- **The schema is built from nothing** by Flyway, and Hibernate checks every entity against it (`ddl-auto=validate`).
- **The `dev-seed` demo data** fills it through the real feature services, so tests start from the college described in `local-test-data.md`. The accounts get a random password for that run only.
- **Simultaneous requests** are made with `Concurrently.run(...)`, which releases several calls at the same instant, each on its own thread and in its own transaction.
- Tests share the seeded data, so each one works on records no other test changes, and says which in its description.

What they check today:

| Test | Checks |
| --- | --- |
| `DatabaseSchemaMySqlIT` | Every migration applies in order with no gaps, the database uses `utf8mb4`, and seeding again creates nothing. |
| `HostelLocksMySqlIT` | Two applications at once make one; two allocations at once never share a bed, an application, or a student. |
| `ApprovalDecisionLocksMySqlIT` | Two decisions on the same step at once (approve and approve, or approve and reject) decide it once; the other gets 409. |
| `SessionMySqlIT` | Sessions end by signing out, signing out everywhere, idleness, and a disabled account, and stay ended. |
| `SubmissionAttemptLocksMySqlIT` | Several hand-ins at once each get their own attempt number, with no gaps or repeats. |

Run them with Docker running:

```bash
cd backend && mvn verify
```

To run only one: `mvn verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=HostelLocksMySqlIT`.

Without Docker they are skipped, not failed. CI always has Docker, so there they always run.

### When to add one

Add a MySQL test whenever a phase adds a row-lock rule, a capacity limit, a uniqueness rule that a race could break, or anything that depends on how MySQL behaves. New lock rules in the roadmap (registration capacity, event seats, bookings, quiz submissions) each come with one.

## Frontend unit tests

The frontend uses **Vitest** with **React Testing Library** in a simulated browser (jsdom). Tests sit next to the code they check, named `*.test.ts` or `*.test.tsx`, and never reach a server: the API client gets a placeholder address, and API answers are faked with an Axios adapter.

```bash
cd frontend && npm test
```

`npm run test:watch` reruns them while you edit. Every test starts with a clean page and empty browser storage (`src/test/setup.ts`).

What they check today:

| Test | Checks |
| --- | --- |
| `login/portal.test.ts` | Only `erp` and `lms` are portals; LMS sign-ins land on Courses or Teaching only for single-role accounts, never by picking a main role. |
| `auth/roles.test.ts` | Roles are kept in the fixed order Student, Faculty, Admin; repeats and unknown names are dropped. |
| `auth/session.test.ts` | The in-memory session comes from the backend's answer; an answer without a usable session is refused. |
| `api/apiClient.test.ts` | Requests go to `/api/v1` with the session cookie and the XSRF setup and no `Authorization` header; a 401 ends the session, but a failed login, a session check, and a 403 never do. |
| `components/navigation/navigation.test.ts` | Role sections appear in the fixed order, shared items once, upcoming items without links, and every link stays inside the app. |
| `ui/Tabs.test.tsx` | The in-page tabs follow the ARIA tabs pattern and work by click and with the arrow keys, Home, and End. |
| `api/useApiQuery.test.tsx` | Cached loading gives the same page states as before, shares one request between pages, never retries 401, 403, or 404, and `retry()` starts again showing loading. |
| `auth/AuthProvider.test.tsx` | The provider asks the backend who is signed in and shows nothing protected meanwhile, keeps nothing in browser storage, signs out on the server and forgets the session even if that fails, and empties the query cache on every change of session. |

Test what a person sees and does: find elements by role and accessible name (`getByRole('tab', { name: 'Home' })`), act with `userEvent`, and avoid class names and internal state. Hooks that use the query cache are rendered with `testQueryClient()` from `src/test/queryTestUtils.tsx`, which turns retries off. Add tests with every phase that changes frontend rules or shared components.

## End-to-end tests

Playwright drives a real Chromium browser through whole journeys against the complete stack (frontend, Nginx, backend, MySQL, MinIO) running in Docker with the demo data. They are the only tests that see everything working together.

```bash
cd e2e
npm ci
npx playwright install chromium   # once
./stack.sh up                     # build and start the stack, a few minutes the first time
npm test
./stack.sh down                   # stop it and delete its data
```

`./stack.sh` runs the stack as its own Compose project, **`stacc-e2e`**, on its own ports (frontend 3100, backend 8180, MySQL 3406, MinIO 9100 and 9101) with its own volumes, so it runs next to your normal `stacc` stack without touching it. On the first `up` it generates every password and secret into an env file outside the repository (`$TMPDIR/stacc-e2e.env`, or `E2E_ENV_FILE`), and `down` deletes the file with the stack. It starts Compose with a clean environment, so settings exported in your shell (such as `SPRING_PROFILES_ACTIVE` or `JWT_SECRET`) never leak in. `./stack.sh logs backend` shows a service's log.

The tests read the demo password from that file and never print it. The journeys share one seeded database and some change it, so they run one at a time in file order, and they are written so they can be run again on the same stack. A failing journey keeps a screenshot and a trace (`npx playwright show-trace e2e/test-results/.../trace.zip`). Flaky journeys are fixed, never retried.

What they check today:

| Journey | Checks |
| --- | --- |
| `01-sign-in.spec.ts` | The front page offers ERP and LMS logins; the session token is in an HttpOnly cookie the page cannot read, nothing is in browser storage, and a reload keeps the person signed in; LMS sign-ins land on Courses or Teaching for single-role accounts and on the dashboard for a student who also teaches; ERP opens the dashboard; a wrong password gets the one generic message; after signing out, pages need signing in again. |
| `02-student-submission.spec.ts` | A student opens a course and an assignment and hands in an attempt, which is accepted on time. |
| `03-faculty-grading.spec.ts` | A faculty member opens the course's submissions, grades the student's latest attempt, and the student then sees the feedback. |
| `04-password-change.spec.ts` | A student changes their password: this device stays signed in, another device is signed out, the old password stops working, and the password is changed back. Only this journey uses `student2`. |
| `05-erp-admin.spec.ts` | An admin filters and searches the course catalog, sees the demo sections with their students, opens a course offering's roster, and sees who teaches CS301 in the offerings list and on its Faculty Assignments page. |
| `06-academic-profile.spec.ts` | A student opens their academic profile from the Account menu and sees their roll number, where they study now, and an earlier semester's published result, with no GPA or percentage. |

Find elements by role and accessible name, as a person would. Every phase that adds a user-facing flow adds or extends a journey.

## AI service tests

```bash
cd ai-service && python -m pytest -q
```

They use the deterministic fake model and embeddings, so they need no model, GPU, or network. See `ai-learning.md`.
