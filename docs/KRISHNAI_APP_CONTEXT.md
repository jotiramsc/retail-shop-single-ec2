# Krishnai App Context

Last updated: 2026-05-23

This is the single handoff file for Krishnai App development, operations, and deployment. Use it before starting new work so the repo can be understood without reading old chat history.

## Current Production

- Public site: https://kpskrishnai.com
- Health check: https://kpskrishnai.com/actuator/health
- AWS region: `us-east-1`
- CloudFormation stack: `retail-shop-single`
- EC2 instance id: `i-0adf12f0cf424fad7`
- Artifact bucket: `retail-shop-single-artifactbucket-x5x2zrjqspuk`
- App config secret ARN: `arn:aws:secretsmanager:us-east-1:242770804747:secret:retail-shop/single/app-config-FSnWOR`
- Postgres secret ARN: `arn:aws:secretsmanager:us-east-1:242770804747:secret:retail-shop/single/postgres-EwhcNg`
- Current deployment model: one Docker image serving React + Spring Boot on one EC2 host, local PostgreSQL persisted on EBS.

Do not use the older suspended-account resources or any Elastic Beanstalk bundle. The active production stack is `retail-shop-single`.

## Repository Layout

```text
.
├── backend/                  Spring Boot API, services, database schema, tests
├── frontend/                 React/Vite admin and customer storefront
├── database/                 database helper files
├── docs/                     project docs and this context file
├── infra/cloudformation/     single EC2 CloudFormation stack
├── scripts/cloudformation/   stack deploy helper
├── scripts/ec2/              EC2 bootstrap, deploy, backup, secrets helpers
├── marketing/                marketing/brochure artifacts
├── sneat-1.0.0/              local Sneat reference assets
├── Dockerfile                production single-image build
└── docker-compose.yml        local Docker runtime
```

## Tech Stack

- Backend: Java 17, Spring Boot 3, Maven wrapper
- Frontend: React 18, Vite, Axios, React Router
- Database: PostgreSQL
- Production runtime: Docker on EC2, local PostgreSQL container, EBS persistence
- AWS deploy path: S3 artifact + SSM Run Command

## Local Development

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Backend with local profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Full Docker local stack:

```bash
docker compose up --build
```

## Verification Commands

Frontend build:

```bash
cd frontend
npm run build
```

Backend package:

```bash
cd backend
./mvnw -q -DskipTests package
```

Targeted tests used for recent AI/category/auth work:

```bash
cd backend
./mvnw -q test -Dtest=ProductCategoryOptionServiceImplTest,CustomerAuthServiceImplTest
```

Production health and currently served assets:

```bash
curl -sS https://kpskrishnai.com/actuator/health
curl -sS https://kpskrishnai.com/app \
  | perl -ne 'print "$1\n" while /(?:src|href)="([^"]*assets\/index-[^"]+)"/g'
```

## Manual EC2 Deployment

Use this when deploying from the local repo. It packages the current `HEAD`, uploads it to the active artifact bucket, and asks the EC2 instance to deploy using SSM.

```bash
set -euo pipefail

COMMIT=$(git rev-parse HEAD)
SHORT=$(git rev-parse --short HEAD)
TS=$(date +%Y%m%d%H%M%S)
ARCHIVE=/private/tmp/retail-shop-release-${SHORT}-${TS}.tar.gz

git archive --format=tar.gz --output="$ARCHIVE" HEAD

BUCKET=retail-shop-single-artifactbucket-x5x2zrjqspuk
KEY=manual-releases/${COMMIT}/release.tar.gz
aws s3 cp "$ARCHIVE" "s3://${BUCKET}/${KEY}" --region us-east-1

ARTIFACT_URI=s3://${BUCKET}/${KEY}
APP_SECRET=arn:aws:secretsmanager:us-east-1:242770804747:secret:retail-shop/single/app-config-FSnWOR
DB_SECRET=arn:aws:secretsmanager:us-east-1:242770804747:secret:retail-shop/single/postgres-EwhcNg

jq -n \
  --arg artifact_uri "$ARTIFACT_URI" \
  --arg release_sha "manual-${COMMIT}-$(date +%Y%m%d%H%M%S)" \
  --arg app_secret_arn "$APP_SECRET" \
  --arg db_secret_arn "$DB_SECRET" \
  '{
    commands: [
      "set -euo pipefail",
      "mkdir -p /opt/retail-shop/releases",
      "RELEASE_DIR=/opt/retail-shop/releases/\($release_sha)",
      "rm -rf \"$RELEASE_DIR\"",
      "mkdir -p \"$RELEASE_DIR\"",
      "aws s3 cp \($artifact_uri) /tmp/retail-shop-release.tar.gz",
      "tar -xzf /tmp/retail-shop-release.tar.gz -C \"$RELEASE_DIR\"",
      "chmod +x \"$RELEASE_DIR\"/scripts/ec2/*.sh \"$RELEASE_DIR\"/scripts/cloudformation/*.sh || true",
      "\"$RELEASE_DIR\"/scripts/ec2/deploy-from-bundle.sh \"$RELEASE_DIR\" \($app_secret_arn) \($db_secret_arn)"
    ]
  }' > /tmp/retail-ssm-params.json

aws ssm send-command \
  --region us-east-1 \
  --instance-ids i-0adf12f0cf424fad7 \
  --document-name AWS-RunShellScript \
  --comment "Deploy retail shop release ${COMMIT}" \
  --parameters file:///tmp/retail-ssm-params.json \
  --query 'Command.CommandId' \
  --output text
```

Poll a deploy command:

```bash
aws ssm get-command-invocation \
  --region us-east-1 \
  --command-id <command-id> \
  --instance-id i-0adf12f0cf424fad7 \
  --query '{Status:Status,ResponseCode:ResponseCode,Stdout:StandardOutputContent,Stderr:StandardErrorContent}' \
  --output json
```

## GitHub Deployment

Push deploy is wired through:

- `.github/workflows/deploy-single-ec2.yml`
- `infra/cloudformation/single-ec2-stack.yaml`
- `scripts/ec2/deploy-from-bundle.sh`
- `scripts/ec2/deploy-single-host.sh`

The workflow packages the repo, uploads a release artifact to S3, and deploys on EC2 through SSM. Keep repository variables aligned with the `retail-shop-single` stack unless intentionally deploying a new environment.

## EC2 Runtime Paths

- Work root: `/opt/retail-shop`
- Current release symlink: `/opt/retail-shop/current`
- Releases: `/opt/retail-shop/releases`
- App env file generated from Secrets Manager: `/opt/retail-shop/app.env`
- Postgres env file generated from Secrets Manager: `/opt/retail-shop/postgres.env`
- Persistent data mount: `/mnt/retail-data`
- Postgres data: `/mnt/retail-data/postgres`
- Backups: `/mnt/retail-data/backups`

## Backup And Restore

Deploys create a PostgreSQL dump before replacing the app container.

Manual backup on EC2:

```bash
POSTGRES_PASSWORD=... ./scripts/ec2/backup-postgres.sh
```

Restore on EC2:

```bash
docker exec -i retail-postgres pg_restore \
  -U <user> \
  -d <db> \
  --clean \
  --if-exists \
  < /mnt/retail-data/backups/<dump-file>
```

## Permissions Model

Staff users authenticate through `/api/auth/login`. Non-admin users should receive effective parent permissions from `StaffUserService.getEffectivePermissions`, so a child access like `BILLING_CHECKOUT` also grants parent `BILLING` for backend APIs that need it.

Frontend menu access is driven by `auth.permissions` in `frontend/src/App.jsx`. Page APIs should still enforce backend permissions with `@PreAuthorize`.

When a user can open a page, make sure the backend allows the read operations required for that page. Write/destructive operations should stay separately restricted.

## Current Cleanup Rules

Ignored generated artifacts can be removed any time:

- `backend/target/`
- `frontend/dist/`
- `frontend/node_modules/`
- `.deploy/*.zip`
- `.DS_Store`

Do not commit generated build folders. Recreate them with `npm install`, `npm run build`, or Maven as needed.

The old tracked `.deploy/backend-eb` Elastic Beanstalk snapshot was removed because production now uses the single-EC2 deployment path.

## Useful Docs

- `README.md`: high-level repo overview
- `docs/KRISHNAI_APP_CONTEXT.md`: this canonical handoff and deployment file
- `docs/single-ec2-deployment.md`: detailed EC2 architecture notes
- `docs/github-single-ec2-deploy.md`: GitHub workflow deployment notes
- `docs/security-hardening-plan.md`: hardening checklist
- `docs/marketing-automation-setup.md`: campaign/AI setup notes

## Recent Production Notes

Recent deployed fixes include:

- explicit admin header logout button
- dashboard analytics cards and permission-aware dashboard behavior
- dashboard search autocomplete across allowed entities
- checkout authorization fixes for staff with checkout access
- category icon generation fixes and transparent icon UI defaults
- campaign template library cleanup
- Sneat-style modal/toast utility replacing browser dialogs

