# Docker Development Stack

Docker can run the whole of Stacc with one command: MySQL, the Spring Boot backend, and the React frontend served by Nginx.

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
   v
mysql      MySQL 8.4
```

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

All three are reachable from this machine only, not from the local network.

If a port is already taken, for example `3306` by a MySQL installed on the machine, change it in `.env` with `MYSQL_PORT`, `BACKEND_PORT`, or `FRONTEND_PORT`.

## Everyday commands

| To do this | Run |
| --- | --- |
| Start in the background | `docker compose up -d --build` |
| See what is running and healthy | `docker compose ps` |
| Follow the logs | `docker compose logs -f` |
| Follow one service | `docker compose logs -f backend` |
| Rebuild after changing backend code | `docker compose up -d --build backend` |
| Stop everything and keep the database | `docker compose down` |
| Stop everything and **delete the database** | `docker compose down -v` |

`docker compose down -v` permanently deletes the local Docker database. Use it only when you want to start from an empty database.

## How it fits together

**One address for the browser.** The frontend image is built with `VITE_API_BASE_URL=/api`, so the browser calls the API on the same address the page came from. Nginx passes every `/api/...` request to the backend and keeps the `/api` prefix. Because page and API share one origin, no CORS setting is needed. A Docker name such as `backend` is never built into the frontend: a browser could not resolve it.

**Pages inside the app.** React Router handles pages in the browser. Nginx therefore answers any path that is not a real file with the app itself, so opening an address such as `/courses` directly works. A missing file under `/assets/` is still a real 404.

**The database address.** Inside Docker the backend reaches MySQL as `mysql`, on port `3306`. That is set in `compose.yaml`. The application's own defaults still point at `localhost`, so nothing changes for direct runs.

**Start order.** MySQL starts first. The backend starts only once MySQL is healthy, and the frontend only once the backend is healthy. Each service has a health check, shown by `docker compose ps`.

**Migrations.** Flyway runs inside the backend when it starts, exactly as in a direct run. An empty database gets every migration. There is no separate migration container.

**Data.** The database files live in the Docker volume `stacc_mysql-data`. It survives `docker compose down` and restarts.

**Accounts.** Nothing creates user accounts. The stack starts with the reference data from the migrations only, so the login endpoint answers but nobody can sign in until accounts are added by a later feature.

## Changing `.env` later

- **Ports and token lifetime:** change the value and run `docker compose up -d`.
- **`JWT_SECRET`:** change it and run `docker compose up -d`. Tokens issued before the change stop working.
- **Database name, user, or passwords:** MySQL creates the database and its user only on the very first start, when the volume is empty. Changing these values afterwards does not change the existing database, and the backend will fail to connect. Either keep the original values, or run `docker compose down -v` and start again with an empty database.

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

A mixed setup also works: start only the database with `docker compose up -d mysql`, and run the backend directly with `MYSQL_HOST=localhost` and the `MYSQL_PORT` from `.env`.

## If something goes wrong

| What you see | What to do |
| --- | --- |
| `required variable ... is missing a value` | Fill in that variable in `.env`. |
| `port is already allocated` | Change the port in `.env`. |
| The backend stops with a message about `JWT_SECRET` | Create a new secret with `openssl rand -base64 48`. |
| The backend stops with `Access denied for user` | The passwords in `.env` no longer match the existing database. See "Changing `.env` later". |
| `/api/...` returns 502 on port 3000 | The backend is not running. Check `docker compose ps` and `docker compose logs backend`. |

## Not included

This is a development and demo stack. It has no domain name, no HTTPS, no production secret handling, and no CI/CD. Redis, Kafka, Kubernetes, and similar tools are not part of it, because nothing in Stacc needs them yet.
