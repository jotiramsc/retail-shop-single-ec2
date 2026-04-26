#!/bin/sh
set -eu

POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-retail-postgres}"
POSTGRES_DB="${POSTGRES_DB:-retail_shop}"
POSTGRES_USER="${POSTGRES_USER:-retail_user}"
DATA_ROOT="${DATA_ROOT:-/mnt/retail-data}"
BACKUP_DIR="${BACKUP_DIR:-$DATA_ROOT/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="$BACKUP_DIR/${POSTGRES_DB}-${TIMESTAMP}.dump"

docker exec "$POSTGRES_CONTAINER_NAME" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$BACKUP_FILE"
find "$BACKUP_DIR" -type f -name '*.dump' -mtime +"$RETENTION_DAYS" -delete

echo "Created backup: $BACKUP_FILE"
