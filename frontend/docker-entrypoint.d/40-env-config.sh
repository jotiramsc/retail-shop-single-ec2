#!/bin/sh
set -eu

: "${APP_API_BASE_URL:=/api}"
: "${GOOGLE_MAPS_API_KEY:=}"
: "${GOOGLE_CLIENT_ID:=647290985970-rhhho15u57fp07jrj75him17gfvjnhc4.apps.googleusercontent.com}"

envsubst '${APP_API_BASE_URL} ${GOOGLE_MAPS_API_KEY} ${GOOGLE_CLIENT_ID}' \
  < /opt/app-config/env-config.template.js \
  > /usr/share/nginx/html/env-config.js
