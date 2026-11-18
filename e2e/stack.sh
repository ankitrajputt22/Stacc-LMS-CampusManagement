#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT=stacc-e2e
TMP_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
ENV_FILE="${E2E_ENV_FILE:-${TMP_DIR%/}/stacc-e2e.env}"

compose() {
  env -i HOME="$HOME" PATH="$PATH" \
    ${DOCKER_HOST:+DOCKER_HOST="$DOCKER_HOST"} \
    ${DOCKER_CONTEXT:+DOCKER_CONTEXT="$DOCKER_CONTEXT"} \
    ${DOCKER_CONFIG:+DOCKER_CONFIG="$DOCKER_CONFIG"} \
    docker compose --project-name "$PROJECT" --env-file "$ENV_FILE" "$@"
}

case "${1:-}" in
  up)
    if [ ! -f "$ENV_FILE" ]; then
      umask 077
      cat > "$ENV_FILE" <<EOF
MYSQL_DATABASE=stacc
MYSQL_USER=stacc
MYSQL_PASSWORD=$(openssl rand -hex 24)
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
MINIO_ROOT_USER=stacc-e2e
MINIO_ROOT_PASSWORD=$(openssl rand -hex 24)
SPRING_PROFILES_ACTIVE=dev-seed
STACC_TEST_PASSWORD=$(openssl rand -hex 16)
MYSQL_PORT=${E2E_MYSQL_PORT:-3406}
BACKEND_PORT=${E2E_BACKEND_PORT:-8180}
FRONTEND_PORT=${E2E_FRONTEND_PORT:-3100}
MINIO_API_PORT=${E2E_MINIO_API_PORT:-9100}
MINIO_CONSOLE_PORT=${E2E_MINIO_CONSOLE_PORT:-9101}
EOF
    fi
    compose up --detach --build --wait
    echo "The end-to-end stack is up at http://localhost:$(sed -n 's/^FRONTEND_PORT=//p' "$ENV_FILE")"
    ;;
  down)
    if [ -f "$ENV_FILE" ]; then
      compose down --volumes --remove-orphans
      rm -f "$ENV_FILE"
    else
      echo "No end-to-end stack settings found at $ENV_FILE; nothing to stop."
    fi
    ;;
  logs)
    compose logs --no-color "${@:2}"
    ;;
  *)
    echo "Usage: $0 up | down | logs [service]" >&2
    exit 2
    ;;
esac
