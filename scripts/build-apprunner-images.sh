#!/bin/sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"

FRONTEND_IMAGE="${FRONTEND_IMAGE:-retail-shop-frontend:apprunner}"
BACKEND_IMAGE="${BACKEND_IMAGE:-retail-shop-backend:apprunner}"

echo "Building backend image: $BACKEND_IMAGE"
docker build -t "$BACKEND_IMAGE" "$ROOT_DIR/backend"

echo "Building frontend image: $FRONTEND_IMAGE"
docker build -t "$FRONTEND_IMAGE" "$ROOT_DIR/frontend"

echo "Done."
echo "Backend image:  $BACKEND_IMAGE"
echo "Frontend image: $FRONTEND_IMAGE"
