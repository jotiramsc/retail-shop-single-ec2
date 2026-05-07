#!/bin/sh
set -eu

: "${APP_API_BASE_URL:=/api}"
: "${GOOGLE_MAPS_API_KEY:=}"

envsubst '${APP_API_BASE_URL} ${GOOGLE_MAPS_API_KEY}' \
  < /opt/app-config/env-config.template.js \
  > /usr/share/nginx/html/env-config.js
