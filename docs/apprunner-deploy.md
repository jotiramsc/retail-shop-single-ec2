# App Runner Deployment

This project is prepared for deployment as two single-container images:

- `backend/Dockerfile`
- `frontend/Dockerfile`

## Important note

AWS App Runner deploys from container images in Amazon ECR. It does not deploy directly from a local Docker image tarball.

## Build local images

```sh
./scripts/build-apprunner-images.sh
```

## Push images to ECR

```sh
./scripts/push-apprunner-images-to-ecr.sh <aws-account-id> us-east-1
```

## App Runner services

Create two services in `us-east-1`:

1. Backend App Runner service
2. Frontend App Runner service

## Backend App Runner configuration

- Image port: `8080`
- Environment variables:

```text
PORT=8080
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/<db-name>
DB_USERNAME=<db-user>
DB_PASSWORD=<db-password>
CORS_ALLOWED_ORIGINS=https://<frontend-service-url>
```

## Frontend App Runner configuration

- Image port: `80`
- Environment variables:

```text
APP_API_BASE_URL=https://<backend-service-url>/api
```

The frontend image reads `APP_API_BASE_URL` at container startup and writes it into `env-config.js`, so you do not need to rebuild the image when the backend URL changes.
