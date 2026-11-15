# Operations: Health Checks and Request IDs

How to tell whether Stacc is healthy, and how to find what happened to one request.

## Health checks

The backend reports its health through Spring Boot Actuator at `/actuator/health`. Only the health endpoint is exposed; every other Actuator endpoint answers 404 (the optional `monitoring` profile below adds metrics on a separate internal port). It lives outside `/api`, so Nginx never passes it to a browser, and the backend's own port is bound to this machine only (`127.0.0.1:8080` in Docker).

| Address | Answers UP when | Used by |
| --- | --- | --- |
| `/actuator/health/liveness`, also `/livez` | The process runs. | Restarting a stuck process (later, for orchestration). |
| `/actuator/health/readiness`, also `/readyz` | Start-up is finished (Flyway migrations included) and the database answers. | Docker's backend health check (`/readyz`), so the frontend starts only when the API can serve. |
| `/actuator/health` | Every part is UP. | People and the system health page. |

`/livez` and `/readyz` always stay on the main port, also when the `monitoring` profile moves Actuator to its internal port.

`/actuator/health` lists each part's status, never details such as addresses, bucket names, or credentials:

| Part | Meaning |
| --- | --- |
| `db` | MySQL answers. |
| `storage` | The private file store (MinIO) answers. Uploads and downloads need it; everything else works without it. |
| `aiService` | `UNKNOWN` while AI is switched off; otherwise whether the internal AI service answers its token-protected health check. Only AI features need it. |

Storage and AI are left out of readiness on purpose: while either is away, the rest of Stacc keeps serving, and their features answer with a clear error (AI routes with 503).

```bash
curl -s http://localhost:8080/actuator/health/readiness
```

## System health page (v0.16.7)

Admins (`ADMIN` with `SYSTEM_HEALTH_VIEW`, added by `V128`) open **System health** in the Manage tray (`/admin/system`, from `GET /api/v1/admin/system`). It reads, live on each visit: the overall status and each part's status from Actuator; the database schema version and number of migrations from Flyway; whether email, AI, and metrics are on and the active profiles; and the JVM's start time, uptime, memory, processors, and threads. It shows statuses and numbers only, never addresses, credentials, or other settings, and stores nothing. "Check again" asks once more; the page never polls.

## Metrics (the `monitoring` profile)

Metrics are off by default. To turn them on:

1. Add `monitoring` to `SPRING_PROFILES_ACTIVE` (for example `prod,monitoring`). The backend then moves Actuator to the internal management port 8081 (`application-monitoring.properties`) and exposes `health` and `prometheus` there, nothing else. The main port keeps `/livez` and `/readyz`, and `/actuator/prometheus` on it stays 404.
2. Start Docker with `docker compose --profile monitoring up`. This adds `prometheus` (pinned to `prom/prometheus:v3.5.0`), which reads `backend:8081/actuator/prometheus` every 30 seconds (`monitoring/prometheus.yml`) and keeps its data in the `prometheus-data` volume. Its own page is published on this machine only, at `http://127.0.0.1:${PROMETHEUS_PORT:-9090}`.

Port 8081 is never published and Nginx never proxies it, so browsers cannot reach the metrics. The metrics are Micrometer's standard ones (requests by route and status, JVM, database pool), tagged `application="stacc-backend"`, with no personal data.

## Request IDs

Every request gets one ID that ties together what a person saw and what the servers logged:

- Nginx gives each request its own ID (`$request_id`) and sends it as `X-Request-ID`. A backend run without Nginx makes a random one.
- The backend keeps a well-formed incoming ID (8 to 64 letters, digits, `.`, `_`, or `-`) and replaces anything else, then returns it in the `X-Request-ID` response header and in every error body as `requestId` (see `api-error-handling.md`).
- Every backend log line written while handling the request shows it in brackets, for example `[a1b2c3d4e5f60718]`; lines outside a request show `[]`.
- Calls to the internal AI service carry it too, and the AI service logs it as `request=…`, so one ID finds the lines in both services.

To follow a problem a person reports, ask for the reference shown with the error, then search the logs for it:

```bash
docker compose logs backend | grep a1b2c3d4e5f60718
```

The ID is only a reference: it grants nothing, decides nothing, and holds no personal data. Never log request bodies, passwords, tokens, or private content next to it.
