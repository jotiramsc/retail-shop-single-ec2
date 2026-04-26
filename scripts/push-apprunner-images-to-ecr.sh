#!/bin/sh
set -eu

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <aws-account-id> [region]"
  exit 1
fi

AWS_ACCOUNT_ID="$1"
AWS_REGION="${2:-us-east-1}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"

FRONTEND_REPO="${FRONTEND_REPO:-retail-shop-frontend}"
BACKEND_REPO="${BACKEND_REPO:-retail-shop-backend}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

FRONTEND_LOCAL_IMAGE="${FRONTEND_LOCAL_IMAGE:-$FRONTEND_REPO:apprunner}"
BACKEND_LOCAL_IMAGE="${BACKEND_LOCAL_IMAGE:-$BACKEND_REPO:apprunner}"

ECR_HOST="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
FRONTEND_REMOTE_IMAGE="$ECR_HOST/$FRONTEND_REPO:$IMAGE_TAG"
BACKEND_REMOTE_IMAGE="$ECR_HOST/$BACKEND_REPO:$IMAGE_TAG"

aws ecr describe-repositories --repository-names "$FRONTEND_REPO" --region "$AWS_REGION" >/dev/null 2>&1 \
  || aws ecr create-repository --repository-name "$FRONTEND_REPO" --region "$AWS_REGION" >/dev/null

aws ecr describe-repositories --repository-names "$BACKEND_REPO" --region "$AWS_REGION" >/dev/null 2>&1 \
  || aws ecr create-repository --repository-name "$BACKEND_REPO" --region "$AWS_REGION" >/dev/null

aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_HOST"

docker build -t "$BACKEND_LOCAL_IMAGE" "$ROOT_DIR/backend"
docker build -t "$FRONTEND_LOCAL_IMAGE" "$ROOT_DIR/frontend"

docker tag "$BACKEND_LOCAL_IMAGE" "$BACKEND_REMOTE_IMAGE"
docker tag "$FRONTEND_LOCAL_IMAGE" "$FRONTEND_REMOTE_IMAGE"

docker push "$BACKEND_REMOTE_IMAGE"
docker push "$FRONTEND_REMOTE_IMAGE"

echo "Backend image pushed:  $BACKEND_REMOTE_IMAGE"
echo "Frontend image pushed: $FRONTEND_REMOTE_IMAGE"
