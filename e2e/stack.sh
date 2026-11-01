#!/usr/bin/env bash
# Starts and stops the throwaway Docker stack the end-to-end tests run against.
#
#   ./e2e/stack.sh up     build and start it, with the demo data, and wait until every service is healthy
#   ./e2e/stack.sh down   stop it and delete its data
#
# It is its own Compose project, "stacc-e2e", with its own ports, volumes, and generated passwords, so it never touches
# the "stacc" project or its data. The passwords live in an env file outside the repository (E2E_ENV_FILE, by default
# in the temporary folder), created on the first "up" and deleted on "down". Nothing is printed from it.
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT=stacc-e2e
TMP_DIR="${TMPDIR:-/tmp}"
ENV_FILE="${E2E_ENV_FILE:-${TMP_DIR%/}/stacc-e2e.env}"

# Compose lets variables already set in the shell win over the env file, so it runs with a clean environment: only
# what Docker itself needs to find the engine. Nothing from this machine's own Stacc settings can leak in.
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
      # Only this project's containers, network, and volumes; never another project's.
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
