# Configuration

Every setting Stacc reads, in one place. Values come from environment variables; Docker Compose reads them from `.env`, and a backend run with Maven reads them from the shell. Never commit real values: `.env` is ignored by Git, and `.env.example` lists the variables with safe placeholders.

## Profiles

| Profile | Used for | How |
| --- | --- | --- |
| *(none)* | Development on a laptop: the defaults point at `localhost` (MySQL 3306, MinIO 9000, frontend dev server 5173), and the API description is open at `/swagger-ui.html`. | The default. |
| `dev-seed` | Adds the demo accounts and data of `local-test-data.md` on top of development. Never for real data. | `SPRING_PROFILES_ACTIVE=dev-seed` |
| `prod` | A real deployment. Host and database names have no defaults, logs are JSON, the API description is off, and `ProductionSettingsCheck` refuses unsafe combinations before anything starts. | `SPRING_PROFILES_ACTIVE=prod` |

Tests set their own settings in their base classes (`DatabaseFreeApiTest`, `MySqlIntegrationTest`, the end-to-end `stack.sh`) and never need a profile or a real secret.

### What `prod` refuses

Start-up stops, listing every problem at once and never printing a value, when:

- the `dev-seed` profile is also active, or `STACC_TEST_PASSWORD` is set;
- `MYSQL_USER` is `root`;
- `STACC_STORAGE_ACCESS_KEY` or `STACC_STORAGE_SECRET_KEY` is empty;
- `FRONTEND_ORIGIN` lists anything but `https` origins of real hosts (no `http`, no `localhost`);
- `STACC_SESSION_SECURE_COOKIE` is not `true`;
- `AI_ENABLED=true` without a `STACC_AI_INTERNAL_TOKEN` of at least 32 characters.
- `MAIL_ENABLED=true` with a `MAIL_LINK_BASE_URL` that is not https.

These checks apply everywhere, `prod` or not: a missing or short `JWT_SECRET`, session lengths outside their ranges, a wildcard or path in `FRONTEND_ORIGIN`, and a missing storage endpoint or bucket.

## Backend

| Variable | Default (development) | Required in `prod` | Secret | Meaning |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | none | `prod` | | The profiles above. |
| `MYSQL_HOST` | `localhost` | yes | | Database host. Docker sets `mysql`. |
| `MYSQL_PORT` | `3306` | | | Database port. |
| `MYSQL_DATABASE` | `stacc` | yes | | Database name. |
| `MYSQL_USER` | none | yes, not `root` | | The application's database account. |
| `MYSQL_PASSWORD` | none | yes | **yes** | Its password. |
| `JWT_SECRET` | none | yes | **yes** | Base64 of at least 32 random bytes (`openssl rand -base64 48`). |
| `STACC_SESSION_MAX_DURATION` | `12h` | | | The longest a sign-in lasts, 15m to 24h. |
| `STACC_SESSION_IDLE_TIMEOUT` | `2h` | | | A sign-in ends after this long without a request; at least 5m. |
| `STACC_SESSION_SECURE_COOKIE` | `false` (`prod`: `true`) | must be `true` | | Sends the session cookie over HTTPS only. |
| `STACC_SIGN_IN_ACCOUNT_THRESHOLD` | `5` | | | Failed sign-ins for one login ID within the window before tries slow down; 3 to 50. |
| `STACC_SIGN_IN_ADDRESS_THRESHOLD` | `30` | | | The same for one network address; at least the account threshold, at most 500. |
| `STACC_SIGN_IN_WINDOW` | `15m` | | | How long failures are remembered; 1m to 24h. |
| `STACC_SIGN_IN_BASE_DELAY` | `30s` | | | The first wait once a threshold is reached, doubling with each further failure; at least 1s. |
| `STACC_SIGN_IN_MAX_DELAY` | `15m` | | | The longest wait; from the base delay up to 1h. |
| `STACC_SIGN_IN_TRUST_PROXY` | `false` | | | Read the client address from Stacc's own proxy (`X-Real-IP`). Only when the backend is reachable through that proxy alone; the Docker stack sets `true`. |
| `FRONTEND_ORIGIN` | `http://localhost:5173` (`prod`: none) | | | Exact origins allowed to call the API from another address, comma-separated. Same-origin setups need none. |
| `STACC_STORAGE_ENDPOINT` | `http://localhost:9000` | yes | | The private S3-compatible store. Docker sets `http://minio:9000`. |
| `STACC_STORAGE_ACCESS_KEY` | empty | yes | | Its access key. |
| `STACC_STORAGE_SECRET_KEY` | empty | yes | **yes** | Its secret key. |
| `STACC_STORAGE_BUCKET` | `stacc-materials` | | | The one private bucket, created on the first upload. |
| `STACC_MATERIAL_MAX_FILE_SIZE` | `25MB` | | | Largest study-material or submission file. |
| `STACC_SUBMISSION_MAX_FILES` | `3` | | | Most files in one submission attempt. |
| `STACC_UPLOAD_MAX_REQUEST_SIZE` | `80MB` | | | Spring's hard limit for one upload request. |
| `API_DOCS_ENABLED` | `false` (only read in `prod`) | | | Opens the API description and its page in production. |
| `STACC_TEST_PASSWORD` | none | must be unset | **yes** | The one password of the `dev-seed` demo accounts. |
| `AI_ENABLED` | `false` | | | Switches the AI learning features on. |
| `AI_SERVICE_URL` | `http://localhost:8095` | if AI is on | | The internal AI service. Docker sets `http://ai-service:8000`. |
| `STACC_AI_INTERNAL_TOKEN` | empty | if AI is on, 32+ characters | **yes** | The shared secret between the backend and the AI service. |
| `AI_CONNECT_TIMEOUT`, `AI_READ_TIMEOUT`, `AI_SYNC_READ_TIMEOUT` | `3s`, `120s`, `15m` | | | How long the backend waits for the AI service. |
| `AI_MAX_SYNC_SOURCES`, `AI_MAX_SYNC_BYTES`, `AI_BUILDING_TIMEOUT` | `100`, `40MB`, `30m` | | | Limits of one knowledge refresh. See `ai-learning.md`. |
| `MAIL_ENABLED` | `false` | | | Emails notifications to people who chose email. See `notification-settings.md`. |
| `MAIL_FROM` | empty | if mail is on | | The sender address of notification emails. |
| `MAIL_LINK_BASE_URL` | `http://localhost:5173` | https if mail is on | | The frontend address that links in emails start with. |
| `MAIL_DIGEST_CRON` | `0 0 7 * * *` | | | When the daily digest is sent, in UTC. |
| `MAIL_HOST`, `MAIL_PORT` | `localhost`, `25` | if mail is on | | The SMTP server. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | empty | | **yes** (password) | The SMTP account, if the server needs one. |
| `MAIL_STARTTLS` | `true` | | | Whether to upgrade the SMTP connection to TLS. |
| `COLLEGE_TIME_ZONE` | `Asia/Kolkata` | | | The college's IANA time zone: facility opening hours and closed days are read in it. |

## AI service

| Variable | Default | Secret | Meaning |
| --- | --- | --- | --- |
| `STACC_AI_INTERNAL_TOKEN` | none (refuses to start) | **yes** | The same shared secret as the backend's. |
| `AI_PROVIDER` | `ollama` | | `ollama`, or `fake` for testing the wiring only. |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | | The Ollama server. Docker sets `http://ollama:11434`. |
| `AI_CHAT_MODEL`, `AI_EMBEDDING_MODEL` | none | | The local models pulled into Ollama. |
| `AI_INDEX_DIR` | `./data/indexes` | | Where course indexes are kept. Docker uses `/data/indexes` on the `ai-index-data` volume. |
| `AI_PROVIDER_TIMEOUT_SECONDS`, `AI_TOP_K`, `AI_MIN_RELEVANCE`, `AI_CHUNK_SIZE`, `AI_CHUNK_OVERLAP`, `AI_EMBEDDING_BATCH_SIZE`, `AI_MAX_*`, `AI_ANSWER_TEMPERATURE`, `AI_DRAFT_TEMPERATURE` | in `ai-service/app/config.py` | | Retrieval, generation, and size limits; the defaults suit a laptop. See `ai-learning.md`. |

## Frontend

| Variable | Default | Meaning |
| --- | --- | --- |
| `VITE_API_BASE_URL` | none (the app refuses to start) | The API root, such as `http://localhost:8080/api`, or `/api` behind Nginx. The app adds the version (`/v1`) itself. Public in the built app: never a secret. |

## Docker Compose only

| Variable | Default | Secret | Meaning |
| --- | --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | none | **yes** | MySQL's root password inside the container. |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | none | password **yes** | The MinIO account; Compose passes it to the backend as its storage keys. |
| `FRONTEND_PORT`, `BACKEND_PORT`, `MYSQL_PORT`, `MINIO_API_PORT`, `MINIO_CONSOLE_PORT` | `3000`, `8080`, `3306`, `9000`, `9001` | | Ports on this machine, all bound to `127.0.0.1`. |

## End-to-end tests only

| Variable | Default | Meaning |
| --- | --- | --- |
| `E2E_ENV_FILE` | `$TMPDIR/stacc-e2e.env` (`$RUNNER_TEMP` on GitHub) | Where `e2e/stack.sh` keeps the generated settings of its throwaway stack. |
| `E2E_BASE_URL` | `http://localhost:3100` | Where the journeys open the app. |
| `E2E_FRONTEND_PORT`, `E2E_BACKEND_PORT`, `E2E_MYSQL_PORT`, `E2E_MINIO_API_PORT`, `E2E_MINIO_CONSOLE_PORT` | `3100`, `8180`, `3406`, `9100`, `9101` | Ports of that stack. |

## Adding a setting

Read it through a `@ConfigurationProperties` record that checks its own values, give it a safe development default (or none, if a wrong default could do harm), add it to `.env.example` with a placeholder, and list it here. If production must set it, leave it without a default in `application-prod.properties`; if a combination is unsafe, add it to `ProductionSettingsCheck`.
