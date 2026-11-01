# Continuous Integration

GitHub Actions checks every change to Stacc automatically, so a broken build or a failing test is noticed before it reaches `development`. The workflow is `.github/workflows/ci.yml`.

## When it runs

- On every push to `development`, `main`, and phase branches (`v0.1-phase-02-ci` and so on).
- On every pull request into `development` or `main`.
- By hand, from the repository's **Actions** tab (**Run workflow**).

A newer push to the same branch cancels a check that is still running for an older one.

## What it checks

| Check | What it runs | Needs |
| --- | --- | --- |
| Backend tests | `mvn -B -ntp verify` in `backend/`: compiles, runs the database-free tests, packages the application, and runs the MySQL tests | Java 21 (Temurin), and Docker for the throwaway MySQL container of the `*MySqlIT` tests (see `testing.md`). |
| Frontend lint, tests, and build | `npm ci`, `npm run lint`, `npm test` (Vitest unit tests), and `npm run build` (type check and production build) in `frontend/` | Node 24. A placeholder public `VITE_API_BASE_URL`, which is not a secret. |
| AI service lint and tests | `ruff check .` and `pytest -q` in `ai-service/` | Python 3.12. No model, GPU, or network: the tests use the fake providers. |
| End-to-end tests | After the backend and frontend checks pass: `./stack.sh up` builds and starts the whole Docker stack with the demo data, then `npm test` runs the Playwright journeys in Chromium, and `./stack.sh down` removes the stack | Docker and Node 24. The stack's passwords are generated for the run into a file in the runner's temporary folder. |

No check uses a stored secret, and the workflow only reads the repository. When backend tests fail, their reports (unit and MySQL) are kept for seven days as the `backend-test-reports` download on the run's page.

## Running the same checks locally

```bash
cd backend && mvn -B verify
```

The MySQL tests need Docker running; without it they are skipped. `mvn test` runs only the database-free tests.

```bash
cd frontend && npm ci && npm run lint && npm test && npm run build
```

```bash
cd ai-service && python -m pip install -r requirements-dev.txt && python -m ruff check . && python -m pytest -q
```

When backend tests fail, a **List failed tests** step names each failed test and its message on the run's summary page.

The frontend build needs `VITE_API_BASE_URL` set, for example from `frontend/.env` (see `frontend-api.md`).

## Rules

- A phase is merged into `development` only after all three checks pass on its branch.
- A failing check is fixed, never skipped or switched off to get a merge through.
- New kinds of checks are added to this workflow in their own phases; see `roadmap.md` and `testing.md`.
- When end-to-end tests fail, the failures appear as annotations, and the HTML report, traces, screenshots, and the backend log are kept for seven days as the `e2e-report` download.
