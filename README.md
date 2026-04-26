# Luxe Retail Studio

Production-oriented retail shop application for a ladies cosmetics and jewellery store with:

- Spring Boot backend
- React frontend
- PostgreSQL database
- Offer automation and mock marketing flows
- Docker-based local runtime
- single-EC2 deployment option with one app image and local PostgreSQL on EBS

The original request mentioned MySQL, but this implementation uses PostgreSQL because you explicitly allowed reusing an existing Postgres/Docker path and asked for future RDS alignment. The architecture stays compatible with a later move to Amazon RDS PostgreSQL with minimal application changes.

## Features

### Phase 1
- POS billing with stock validation
- Auto-application of the single best active offer
- Inventory management with low stock detection
- Customer management with mobile-first records
- Daily sales and low-stock reporting

### Phase 2
- Centralized checkout and billing pricing via `OrderPricingService`
- Single active coupon flow with automatic recalculation across checkout, orders, and billing
- Offer automation for smart suggestions
- WhatsApp OTP and customer messaging over Twilio
- Instagram and Facebook publishing over Meta Graph API
- Campaign draft, publish, retry, and history tracking

## Tech Stack

- Backend: Java 17, Spring Boot 3, Spring Data JPA, Maven Wrapper
- Frontend: React 18, Vite, Axios, React Router
- Database: PostgreSQL 16
- Containers: Docker Compose

## Project Structure

```text
.
├── backend
│   └── src/main/java/com/retailshop
│       ├── config
│       ├── controller
│       ├── dto
│       ├── entity
│       ├── enums
│       ├── exception
│       ├── repository
│       └── service
├── database
├── frontend
│   └── src
│       ├── components
│       ├── pages
│       ├── services
│       ├── styles
│       └── utils
└── docker-compose.yml
```

## Single EC2 Option

This repo also supports a lower-cost single-EC2 deployment model:

- one root `Dockerfile` that packages frontend + backend together
- one local PostgreSQL instance on the EC2 host via Docker
- PostgreSQL data persisted on an attached EBS volume

Runbook:

- [Single EC2 deployment](docs/single-ec2-deployment.md)
- [GitHub deploy for Single EC2](docs/github-single-ec2-deploy.md)

## Run With Docker

Prerequisites:

- Docker Desktop

Start the full stack:

```bash
docker compose up --build
```

Apps:

- Frontend: [http://localhost:5173](http://localhost:5173)
- Backend API: [http://localhost:8080/api](http://localhost:8080/api)
- PostgreSQL: `localhost:5432`

Database defaults:

- DB: `retail_shop`
- User: `retail_user`
- Password: `retail_pass`

## Run Locally Without Docker

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

Run with the local in-memory profile when Postgres is not available:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Run backend tests:

```bash
./mvnw test
```

Override database env vars if needed:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/retail_shop
export DB_USERNAME=retail_user
export DB_PASSWORD=retail_pass
```

Optional integration env vars:

```bash
export TWILIO_ACCOUNT_SID=...
export TWILIO_AUTH_TOKEN=...
export TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
export TWILIO_WHATSAPP_OTP_CONTENT_SID=
export TWILIO_WHATSAPP_ORDER_CONTENT_SID=
export TWILIO_WHATSAPP_OFFER_CONTENT_SID=
export META_ACCESS_TOKEN=
export FB_PAGE_ID=
export IG_BUSINESS_ACCOUNT_ID=
export META_GRAPH_VERSION=v23.0
export CHECKOUT_TAX_PERCENT=0
export CHECKOUT_DELIVERY_CHARGE=0
export CHECKOUT_FREE_DELIVERY_MIN_ORDER=0
export OTP_LENGTH=6
export OTP_MAX_ATTEMPTS=5
export OTP_RATE_LIMIT_SECONDS=30
export OTP_TTL_MINUTES=5
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Optional environment file:

```bash
cp .env.example .env
```

## API Summary

### Billing
- `POST /api/billing/create`
- `GET /api/billing/{id}`

### Products
- `POST /api/products`
- `GET /api/products`
- `PUT /api/products/{id}`

### Customers
- `POST /api/customers`
- `GET /api/customers`
- `GET /api/customers/history?mobile=...`

### Offers
- `POST /api/offers`
- `GET /api/offers`
- `GET /api/offers/suggested`

### Campaigns
- `POST /api/campaign`
- `POST /api/campaign/send`
- `POST /api/campaign/{campaignId}/publish`
- `POST /api/campaign/history/{campaignLogId}/retry`
- `GET /api/campaign/history`

### Reports
- `GET /api/reports/daily`
- `GET /api/reports/low-stock`

## Sample Data

Seeded automatically on startup:

- 3 products
- 2 customers
- 2 active offers

Sample SKUs:

- `COS-LIP-001`
- `JEW-EAR-001`
- `COS-SER-001`

## Business Rules Implemented

- Billing is blocked when stock is insufficient
- Duplicate cart lines are normalized before stock is reduced
- Only one best offer is applied per line item
- Only one coupon can be active at a time and an explicit coupon replaces automatic offer pricing
- Customer OTP uses hashed storage, a 30-second resend cooldown, and max-attempt validation
- Offers must be active and date-valid
- Percent-based offers cannot exceed 100%
- Offer targeting is explicit: product or category, never both
- Customer mobile is required
- Historical invoices remain unchanged even when products are updated later

## Production Notes

- Backend uses DTOs and layered service boundaries for maintainability
- PostgreSQL config is externalized for easy RDS migration
- Stock thresholds and slow-moving automation thresholds are configurable in `application.yml`
- Frontend is built into static assets and served by Nginx in Docker
- The root `Dockerfile` packages the frontend into Spring Boot for a single-container app deploy
- Twilio WhatsApp delivery requires either a registered sender or the joined Twilio sandbox
- Meta publishing requires Page and Instagram Business credentials with publish permissions
- `schema.sql` and `data.sql` bootstrap the database predictably

## Next Production Upgrades

- Move Twilio and Meta secrets from plain ECS env vars into AWS Secrets Manager
- Add richer per-recipient WhatsApp delivery tracking and template management
- Add audit tables and inventory movement ledger
- Add pagination and search endpoints
