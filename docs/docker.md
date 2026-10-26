# Docker Development Stack

Docker can run the whole of Stacc with one command: MySQL, MinIO object storage, the Spring Boot backend, and the React frontend served by Nginx.

```text
Browser
   |
   v
frontend   Nginx, serves the built React app
   |
   |  /api/...
   v
backend    Spring Boot
   |
   +--> mysql   MySQL 8.4: all data, including file details
   |
   +--> minio   MinIO: study-material and submission files, in a private bucket
```

The browser only ever talks to the frontend. It never reaches MinIO: every file upload and download goes through the backend, which checks access first.

Docker is optional. Everything can still be run directly, as described at the end.

## What you need

Docker with Docker Compose. Docker Desktop includes both.

## First run

Create your local configuration from the example:

```bash
cp .env.example .env
```

Open `.env` and fill in the empty values:

- `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD`: passwords of your own choice for this local database.
- `JWT_SECRET`: a long random value. Create one with `openssl rand -base64 48`.
- `MINIO_ROOT_PASSWORD`: a password for the local MinIO account, at least 8 characters. Create one with `openssl rand -hex 24`. `MINIO_ROOT_USER` already has a value you may keep.

Then start everything:

```bash
docker compose up --build
```

The first build downloads images and dependencies and takes a few minutes. Later starts take well under a minute. Press Ctrl+C to stop.

If a required value is missing, Docker Compose refuses to start and names the variable.

`.env` is ignored by Git. Never commit it, and never put real secrets in `.env.example`.

## Addresses

| What | Address |
| --- | --- |
| Frontend | <http://localhost:3000> |
| Backend | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| MySQL | `localhost`, port `3306` |
| MinIO storage API | <http://localhost:9000>, for a backend run with Maven |
| MinIO console | <http://localhost:9001>, sign in with `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD` |

All of them are reachable from this machine only, not from the local network.

If a port is already taken, for example `3306` by a MySQL installed on the machine, change it in `.env` with `MYSQL_PORT`, `BACKEND_PORT`, `FRONTEND_PORT`, `MINIO_API_PORT`, or `MINIO_CONSOLE_PORT`.

## Everyday commands

| To do this | Run |
| --- | --- |
| Start in the background | `docker compose up -d --build` |
| See what is running and healthy | `docker compose ps` |
| Follow the logs | `docker compose logs -f` |
| Follow one service | `docker compose logs -f backend` |
| Rebuild after changing backend code | `docker compose up -d --build backend` |
| Stop everything and keep the database | `docker compose down` |
| Stop everything and **delete the database and stored files** | `docker compose down -v` |

`docker compose down -v` permanently deletes the local Docker database and every uploaded file, study materials and submissions alike. Use it only when you want to start from nothing.

## How it fits together

**One address for the browser.** The frontend image is built with `VITE_API_BASE_URL=/api`, so the browser calls the API on the same address the page came from. Nginx passes every `/api/...` request to the backend and keeps the `/api` prefix. Because page and API share one origin, no CORS setting is needed. A Docker name such as `backend` is never built into the frontend: a browser could not resolve it.

**Signing in.** <http://localhost:3000> opens the sign-in page, and the login goes to `/api/auth/login` through Nginx. The `FRONTEND_ORIGIN` value in `.env` is not needed for this. It only matters if a frontend dev server on this machine (`npm run dev`) calls the Docker backend directly on port 8080.

**Pages inside the app.** React Router handles pages in the browser. Nginx therefore answers any path that is not a real file with the app itself, so opening an address such as `/courses` directly works. A missing file under `/assets/` is still a real 404.

**The database address.** Inside Docker the backend reaches MySQL as `mysql`, on port `3306`. That is set in `compose.yaml`. The application's own defaults still point at `localhost`, so nothing changes for direct runs.

**Start order.** MySQL and MinIO start first. The backend starts only once both are healthy, and the frontend only once the backend is healthy. Each service has a health check, shown by `docker compose ps`; MinIO's asks its own `/minio/health/live` address.

**File storage.** The `minio` service runs `pgsty/minio`, pinned to one release. MinIO stopped publishing official images in 2025, and this is the community-maintained build that follows its releases. Inside Docker the backend reaches it as `http://minio:9000`, set in `compose.yaml`, and signs in with the MinIO account from `.env`. The backend creates its bucket (`stacc-materials` by default, `STACC_STORAGE_BUCKET`) on the first upload and never makes it public. Files are kept under keys the backend generates, never under their original names.

**Upload size.** Every file may be up to 25 MB, and a submission attempt may have up to 3 files. Nginx accepts up to 80 MB on the submission route (`/api/lms/courses/{id}/assignments/{id}/submissions`) and 26 MB on every other request, so a file a little too large gets a clear message from the backend, and anything much larger is refused by Nginx at once.

**Migrations.** Flyway runs inside the backend when it starts, exactly as in a direct run. An empty database gets every migration. There is no separate migration container.

**Data.** The database files live in the Docker volume `stacc_mysql-data` and the uploaded files in `stacc_minio-data`. Both survive `docker compose down` and restarts; only `docker compose down -v` removes them.

**Optional AI learning (profile `ai`).** `docker compose --profile ai up` adds two services, `ai-service` (the internal Python AI service, built from `ai-service/`) and `ollama` (local models, pinned to `ollama/ollama:0.13.0`). Neither publishes a port: only the backend reaches `ai-service`, as `http://ai-service:8000` set in `compose.yaml`, with the shared `STACC_AI_INTERNAL_TOKEN`, and only `ai-service` reaches `ollama`. Nginx proxies `/api` only, with a longer wait on the AI routes, so `/internal` is never reachable from a browser. Indexes live in the `ai-index-data` volume and models in `ollama-data`; models are pulled once by hand, never during a build. Without the profile nothing changes: the four usual services start, and AI routes answer 503. See `ai-learning.md`.

**Accounts.** Nothing creates user accounts. The stack starts with the reference data from the migrations only, so the login endpoint answers but nobody can sign in until accounts are added by a later feature.

## Local test data

An empty database has no accounts. To try the application with test accounts and connected demo data, set `STACC_TEST_PASSWORD` in `.env` and start with the `dev-seed` profile:

```bash
SPRING_PROFILES_ACTIVE=dev-seed docker compose up -d --build
```

It only adds what is missing and is safe to repeat. Without the profile nothing is created. See `local-test-data.md` for the accounts and the data.

## Changing `.env` later

- **Ports and token lifetime:** change the value and run `docker compose up -d`.
- **`JWT_SECRET`:** change it and run `docker compose up -d`. Tokens issued before the change stop working.
- **Database name, user, or passwords:** MySQL creates the database and its user only on the very first start, when the volume is empty. Changing these values afterwards does not change the existing database, and the backend will fail to connect. Either keep the original values, or run `docker compose down -v` and start again with an empty database.
- **MinIO account:** MinIO also keeps its account in its volume. Change `MINIO_ROOT_USER` or `MINIO_ROOT_PASSWORD` only together with `docker compose down -v`, which deletes the stored files.

## Running without Docker

Nothing about the direct way of working has changed:

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm run dev
```

The backend reads the same variables as in `.env.example`, but Spring Boot does not read `.env` files, so set them in your shell or IDE. The frontend dev server reads `frontend/.env` (see `frontend-api.md`).

A mixed setup also works, and is the easiest way to work on the backend: start only the database and the storage with `docker compose up -d mysql minio`, and run the backend directly with `MYSQL_HOST=localhost`, the `MYSQL_PORT` from `.env`, `STACC_STORAGE_ENDPOINT=http://localhost:9000`, and `STACC_STORAGE_ACCESS_KEY` and `STACC_STORAGE_SECRET_KEY` set to the MinIO account. Never put `minio:9000` in the application's settings: that name exists only inside Docker.

The backend starts without storage. Only uploading and downloading files need it, for study materials and submissions; without it they answer that file storage is not available. A text-only submission does not need storage.

## If something goes wrong

| What you see | What to do |
| --- | --- |
| `required variable ... is missing a value` | Fill in that variable in `.env`. |
| `port is already allocated` | Change the port in `.env`. |
| The backend stops with a message about `JWT_SECRET` | Create a new secret with `openssl rand -base64 48`. |
| The backend stops with `Access denied for user` | The passwords in `.env` no longer match the existing database. See "Changing `.env` later". |
| `/api/...` returns 502 on port 3000 | The backend is not running. Check `docker compose ps` and `docker compose logs backend`. |
| AI pages say AI learning features are temporarily unavailable | AI is off (`AI_ENABLED`), the `ai` profile is not running, the tokens differ, or no model is pulled. Check `docker compose --profile ai logs ai-service`. |
| Uploads say file storage is not available | MinIO is not running or the backend's storage account is wrong. Check `docker compose ps` and `docker compose logs minio`. |

## Not included

This is a development and demo stack. It has no domain name, no HTTPS, no production secret handling, and no CI/CD. Redis, Kafka, Kubernetes, and similar tools are not part of it, because nothing in Stacc needs them yet.
