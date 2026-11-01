# Testing

Stacc has several kinds of tests. Each kind answers a different question, and CI runs all of them on every change (see `continuous-integration.md`). New kinds are added by the roadmap's v0.1 phases.

| Kind | Where | Answers | Runs with |
| --- | --- | --- | --- |
| Backend unit and API tests | `backend/src/test`, classes named `*Test` or `*Tests` | Do the business rules, validation, permissions, and API answers work? | `mvn test` |
| Backend MySQL tests | `backend/src/test`, classes named `*MySqlIT` | Do the migrations, constraints, and row locks work on a real MySQL server? | `mvn verify` (needs Docker) |
| Frontend unit tests | `frontend/src`, files named `*.test.ts` or `*.test.tsx` next to the code | Do the frontend's rules (sign-in, roles, navigation, the API client) and shared components behave? | `npm test` |
| AI service tests | `ai-service/tests` | Do extraction, indexing, retrieval, and the internal API work? | `pytest` |

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
| `auth/session.test.ts` | The session end time comes from the login answer; an answer without a usable token is refused. |
| `auth/authStorage.test.ts` | The session lives in this tab's `sessionStorage` only, sign-out forgets it, and damaged data is thrown away. |
| `api/apiClient.test.ts` | The token is sent only while signed in; a refused current token ends the session; a failed login, a 403, and a refused older token never do. |
| `components/navigation/navigation.test.ts` | Role sections appear in the fixed order, shared items once, upcoming items without links, and every link stays inside the app. |
| `ui/Tabs.test.tsx` | The in-page tabs follow the ARIA tabs pattern and work by click and with the arrow keys, Home, and End. |

Test what a person sees and does: find elements by role and accessible name (`getByRole('tab', { name: 'Home' })`), act with `userEvent`, and avoid class names and internal state. Add tests with every phase that changes frontend rules or shared components.

## AI service tests

```bash
cd ai-service && python -m pytest -q
```

They use the deterministic fake model and embeddings, so they need no model, GPU, or network. See `ai-learning.md`.
