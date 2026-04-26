#!/bin/sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"

DATA_ROOT="${DATA_ROOT:-/mnt/retail-data}"
DOCKER_NETWORK="${DOCKER_NETWORK:-retail-shop-net}"
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-retail-postgres}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:18}"
POSTGRES_DB="${POSTGRES_DB:-retail_shop}"
POSTGRES_USER="${POSTGRES_USER:-retail_user}"
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD before running this script.}"

APP_IMAGE="${APP_IMAGE:-retail-shop-single:latest}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-retail-shop-app}"
APP_PREVIOUS_CONTAINER_NAME="${APP_PREVIOUS_CONTAINER_NAME:-retail-shop-app-previous}"
APP_CANDIDATE_CONTAINER_NAME="${APP_CANDIDATE_CONTAINER_NAME:-retail-shop-app-candidate}"
HOST_PORT="${HOST_PORT:-80}"
CANDIDATE_PORT="${CANDIDATE_PORT:-18080}"
APP_ENV_FILE="${APP_ENV_FILE:-$ROOT_DIR/.deploy/ec2/app.env}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
DB_INIT_MODE="${DB_INIT_MODE:-never}"
SEED_SAMPLE_DATA="${SEED_SAMPLE_DATA:-false}"
BACKUP_BEFORE_DEPLOY="${BACKUP_BEFORE_DEPLOY:-true}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:${HOST_PORT}/actuator/health}"
CANDIDATE_HEALTH_URL="http://127.0.0.1:${CANDIDATE_PORT}/actuator/health"

mkdir -p "$DATA_ROOT/postgres" "$DATA_ROOT/backups" "$DATA_ROOT/app"
chown -R 999:999 "$DATA_ROOT/postgres"

docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"

if ! docker ps -a --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER_NAME"; then
  docker run -d \
    --name "$POSTGRES_CONTAINER_NAME" \
    --network "$DOCKER_NETWORK" \
    --restart unless-stopped \
    -e "POSTGRES_DB=$POSTGRES_DB" \
    -e "POSTGRES_USER=$POSTGRES_USER" \
    -e "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" \
    -v "$DATA_ROOT/postgres:/var/lib/postgresql" \
    "$POSTGRES_IMAGE"
else
  docker start "$POSTGRES_CONTAINER_NAME" >/dev/null 2>&1 || true
fi

echo "Waiting for Postgres to become ready..."
until docker exec "$POSTGRES_CONTAINER_NAME" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; do
  sleep 2
done

if [ "$BACKUP_BEFORE_DEPLOY" = "true" ]; then
  "$ROOT_DIR/scripts/ec2/backup-postgres.sh"
fi

docker exec -i "$POSTGRES_CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  < "$ROOT_DIR/backend/src/main/resources/schema.sql"

if [ "$SEED_SAMPLE_DATA" = "true" ]; then
  PRODUCT_COUNT="$(docker exec "$POSTGRES_CONTAINER_NAME" psql -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'select count(*) from products')"
  if [ "${PRODUCT_COUNT:-0}" = "0" ]; then
    docker exec -i "$POSTGRES_CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
      < "$ROOT_DIR/backend/src/main/resources/data.sql"
  fi
fi

echo "Building single-image application..."
docker build -t "$APP_IMAGE" -f "$ROOT_DIR/Dockerfile" "$ROOT_DIR"

docker rm -f "$APP_CANDIDATE_CONTAINER_NAME" >/dev/null 2>&1 || true

set -- docker run -d \
  --name "$APP_CANDIDATE_CONTAINER_NAME" \
  --network "$DOCKER_NETWORK" \
  --restart unless-stopped \
  -p "127.0.0.1:${CANDIDATE_PORT}:8080"

if [ -f "$APP_ENV_FILE" ]; then
  set -- "$@" --env-file "$APP_ENV_FILE"
fi

set -- "$@" \
  -e "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE" \
  -e "DB_INIT_MODE=$DB_INIT_MODE" \
  -e "DB_URL=jdbc:postgresql://${POSTGRES_CONTAINER_NAME}:5432/${POSTGRES_DB}" \
  -e "DB_USERNAME=$POSTGRES_USER" \
  -e "DB_PASSWORD=$POSTGRES_PASSWORD" \
  "$APP_IMAGE"

"$@"

echo "Waiting for candidate app container..."
ATTEMPT=0
until curl -fsS "$CANDIDATE_HEALTH_URL" >/dev/null 2>&1; do
  ATTEMPT=$((ATTEMPT + 1))
  if [ "$ATTEMPT" -ge 45 ]; then
    docker logs "$APP_CANDIDATE_CONTAINER_NAME" || true
    exit 1
  fi
  sleep 2
done

docker rm -f "$APP_PREVIOUS_CONTAINER_NAME" >/dev/null 2>&1 || true
if docker ps -a --format '{{.Names}}' | grep -qx "$APP_CONTAINER_NAME"; then
  docker stop "$APP_CONTAINER_NAME" >/dev/null
  docker rename "$APP_CONTAINER_NAME" "$APP_PREVIOUS_CONTAINER_NAME"
fi

set -- docker run -d \
  --name "$APP_CONTAINER_NAME" \
  --network "$DOCKER_NETWORK" \
  --restart unless-stopped \
  -p "${HOST_PORT}:8080"

if [ -f "$APP_ENV_FILE" ]; then
  set -- "$@" --env-file "$APP_ENV_FILE"
fi

set -- "$@" \
  -e "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE" \
  -e "DB_INIT_MODE=$DB_INIT_MODE" \
  -e "DB_URL=jdbc:postgresql://${POSTGRES_CONTAINER_NAME}:5432/${POSTGRES_DB}" \
  -e "DB_USERNAME=$POSTGRES_USER" \
  -e "DB_PASSWORD=$POSTGRES_PASSWORD" \
  "$APP_IMAGE"

if ! "$@"; then
  docker rm -f "$APP_CONTAINER_NAME" >/dev/null 2>&1 || true
  if docker ps -a --format '{{.Names}}' | grep -qx "$APP_PREVIOUS_CONTAINER_NAME"; then
    docker start "$APP_PREVIOUS_CONTAINER_NAME" >/dev/null
  fi
  exit 1
fi

echo "Waiting for live app container..."
ATTEMPT=0
until curl -fsS "$HEALTH_URL" >/dev/null 2>&1; do
  ATTEMPT=$((ATTEMPT + 1))
  if [ "$ATTEMPT" -ge 45 ]; then
    docker logs "$APP_CONTAINER_NAME" || true
    docker rm -f "$APP_CONTAINER_NAME" >/dev/null 2>&1 || true
    if docker ps -a --format '{{.Names}}' | grep -qx "$APP_PREVIOUS_CONTAINER_NAME"; then
      docker start "$APP_PREVIOUS_CONTAINER_NAME" >/dev/null
    fi
    exit 1
  fi
  sleep 2
done

docker rm -f "$APP_CANDIDATE_CONTAINER_NAME" >/dev/null 2>&1 || true
docker rm -f "$APP_PREVIOUS_CONTAINER_NAME" >/dev/null 2>&1 || true

echo "Deployment complete. Live health check: $HEALTH_URL"
