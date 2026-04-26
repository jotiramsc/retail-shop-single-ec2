#!/bin/sh
set -eu

RELEASE_DIR="${1:?Provide the extracted release directory as the first argument.}"
APP_SECRET_ID="${2:?Provide the app secret id or arn as the second argument.}"
POSTGRES_SECRET_ID="${3:?Provide the postgres secret id or arn as the third argument.}"

WORK_DIR="${WORK_DIR:-/opt/retail-shop}"
APP_ENV_FILE="${APP_ENV_FILE:-$WORK_DIR/app.env}"
POSTGRES_ENV_FILE="${POSTGRES_ENV_FILE:-$WORK_DIR/postgres.env}"

"$RELEASE_DIR/scripts/ec2/write-env-files-from-secrets.sh" \
  "$APP_SECRET_ID" \
  "$POSTGRES_SECRET_ID" \
  "$APP_ENV_FILE" \
  "$POSTGRES_ENV_FILE"

set -a
. "$POSTGRES_ENV_FILE"
set +a

APP_ENV_FILE="$APP_ENV_FILE" \
POSTGRES_DB="${POSTGRES_DB:-retail_shop}" \
POSTGRES_USER="${POSTGRES_USER:-retail_user}" \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
"$RELEASE_DIR/scripts/ec2/deploy-single-host.sh"

ln -sfn "$RELEASE_DIR" "$WORK_DIR/current"

echo "Release deployed from $RELEASE_DIR"
