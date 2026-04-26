#!/bin/sh
set -eu

APP_SECRET_ID="${1:?Provide the app secret id or arn as the first argument.}"
POSTGRES_SECRET_ID="${2:?Provide the postgres secret id or arn as the second argument.}"
APP_ENV_FILE="${3:-/opt/retail-shop/app.env}"
POSTGRES_ENV_FILE="${4:-/opt/retail-shop/postgres.env}"

mkdir -p "$(dirname "$APP_ENV_FILE")" "$(dirname "$POSTGRES_ENV_FILE")"

aws secretsmanager get-secret-value --secret-id "$APP_SECRET_ID" --query SecretString --output text \
  | jq -r '
      (if type == "string" then fromjson else . end)
      | to_entries[]
      | select(.value != null and .value != "")
      | "\(.key)=\(.value|tostring)"
    ' > "$APP_ENV_FILE"

aws secretsmanager get-secret-value --secret-id "$POSTGRES_SECRET_ID" --query SecretString --output text \
  | jq -r '
      (if type == "string" then fromjson else . end)
      | to_entries[]
      | select(.value != null and .value != "")
      | "\(.key)=\(.value|tostring)"
    ' > "$POSTGRES_ENV_FILE"

chmod 600 "$APP_ENV_FILE" "$POSTGRES_ENV_FILE"

echo "Wrote $APP_ENV_FILE and $POSTGRES_ENV_FILE from Secrets Manager"
