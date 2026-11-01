# Testing

Stacc has several kinds of tests. Each kind answers a different question, and CI runs all of them on every change (see `continuous-integration.md`). New kinds are added by the roadmap's v0.1 phases.

| Kind | Where | Answers | Runs with |
| --- | --- | --- | --- |
| Backend unit and API tests | `backend/src/test`, classes named `*Test` or `*Tests` | Do the business rules, validation, permissions, and API answers work? | `mvn test` |
| Backend MySQL tests | `backend/src/test`, classes named `*MySqlIT` | Do the migrations, constraints, and row locks work on a real MySQL server? | `mvn verify` (needs Docker) |
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

## AI service tests

```bash
cd ai-service && python -m pytest -q
```

They use the deterministic fake model and embeddings, so they need no model, GPU, or network. See `ai-learning.md`.
