# Single EC2 Deployment

This repo now supports a single-EC2 deployment model with:

- one application image built from the root `Dockerfile`
- one local PostgreSQL container on the same EC2 host
- PostgreSQL data stored on an attached EBS volume
- backups written to the same EBS volume before each deploy

## Important reality check

This setup is durable and much cheaper than ECS + RDS, but it is **not** highly available.

What it does protect:

- app redeploys do not delete PostgreSQL data
- EC2 reboots keep containers coming back automatically
- EBS keeps database files outside the app container
- pre-deploy backups protect against bad releases

What it does **not** guarantee:

- true zero downtime for 12 straight months
- protection from a full EC2 host failure without recovery time
- seamless OS patching with no interruption

If you need real zero-downtime and infrastructure fault tolerance, you need at least two app nodes and a managed or replicated database.

## Architecture

- `Dockerfile`
  - builds the React frontend
  - copies the built frontend into Spring Boot static resources
  - packages one backend jar that serves both UI and API
- `scripts/ec2/deploy-single-host.sh`
  - starts PostgreSQL on the local Docker network if needed
  - waits for PostgreSQL readiness
  - takes a `pg_dump` backup
  - applies `schema.sql`
  - builds the single app image
  - health-checks a candidate container
  - rotates the live app container
- `scripts/ec2/mount-ebs-volume.sh`
  - formats and mounts a dedicated EBS volume

## Persistent paths

Default EBS mount point:

```text
/mnt/retail-data
```

Persistent folders created there:

```text
/mnt/retail-data/postgres
/mnt/retail-data/backups
/mnt/retail-data/app
```

The PostgreSQL container stores all database files in `/mnt/retail-data/postgres`, so app deployments do not affect the database files.

## One-time EC2 setup

1. Launch Ubuntu on EC2.
2. Attach a separate EBS volume for data.
3. Clone this repo onto the instance.
4. Run:

```bash
sudo ./scripts/ec2/bootstrap-single-host.sh
sudo EBS_DEVICE=/dev/nvme1n1 ./scripts/ec2/mount-ebs-volume.sh
```

5. Create app env:

```bash
sudo mkdir -p /opt/retail-shop
sudo cp .deploy/ec2/app.env.example /opt/retail-shop/app.env
sudo cp .deploy/ec2/postgres.env.example /opt/retail-shop/postgres.env
```

6. Edit those files with real values.

## Deploy

Run on the EC2 instance:

```bash
set -a
. /opt/retail-shop/postgres.env
set +a

APP_ENV_FILE=/opt/retail-shop/app.env \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
./scripts/ec2/deploy-single-host.sh
```

That script keeps PostgreSQL separate from the app rollout.

## First boot on a fresh database

If you want the sample data on a brand-new empty database:

```bash
SEED_SAMPLE_DATA=true \
APP_ENV_FILE=/opt/retail-shop/app.env \
POSTGRES_PASSWORD=... \
./scripts/ec2/deploy-single-host.sh
```

For a real production migration from the existing database, do **not** seed sample data. Restore the production dump first, then deploy the app.

## Data migration from the current production database

1. Take a dump from the current PostgreSQL source:

```bash
pg_dump -Fc -h <source-host> -U <source-user> -d <source-db> > retail-shop.dump
```

2. Copy the dump to the EC2 instance.
3. Start the local PostgreSQL container with the deploy script.
4. Restore into the local database:

```bash
docker exec -i retail-postgres pg_restore -U <target-user> -d <target-db> --clean --if-exists < /path/to/retail-shop.dump
```

5. Run the deploy script again so `schema.sql` can apply any safe idempotent additions.

## Backups

Manual backup:

```bash
POSTGRES_PASSWORD=... ./scripts/ec2/backup-postgres.sh
```

Recommended cron job:

```cron
0 2 * * * POSTGRES_PASSWORD=... /path/to/repo/scripts/ec2/backup-postgres.sh >> /var/log/retail-shop-backup.log 2>&1
```

## Recovery

Restore a dump file:

```bash
docker exec -i retail-postgres pg_restore -U <user> -d <db> --clean --if-exists < /mnt/retail-data/backups/<dump-file>
```

## Recommended EC2 sizing

For this app on a single host:

- `t4g.medium` is the practical baseline
- `30-50 GB` gp3 EBS is a good starting point

## SSL and domains

This repo change does not add TLS termination by itself. For production HTTPS on a single EC2 host, terminate TLS with Nginx or Caddy on the host and proxy to `localhost:8080`.
