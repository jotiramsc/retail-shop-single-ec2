#!/bin/sh
set -eu

: "${APP_API_BASE_URL:=/api}"

envsubst '${APP_API_BASE_URL}' \
  < /opt/app-config/env-config.template.js \
  > /usr/share/nginx/html/env-config.js
