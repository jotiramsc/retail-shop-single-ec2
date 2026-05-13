# Luxe Retail Studio

Production-oriented retail shop application for a ladies cosmetics and jewellery store with:

- Spring Boot backend
- React frontend
- PostgreSQL database
- Offer automation and AI-assisted marketing automation flows
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
- WhatsApp OTP, marketing, and customer bot messaging over Meta WhatsApp Cloud API
- Instagram and Facebook publishing over Meta Graph API
- Marketing Automation with AI draft generation, approval queue, scheduling, publishing logs, and analytics

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
- [Security hardening plan](docs/security-hardening-plan.md)
- [Marketing Automation setup](docs/marketing-automation-setup.md)

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
export META_ACCESS_TOKEN=
export META_WHATSAPP_PHONE_NUMBER_ID=
export META_WHATSAPP_OTP_TEMPLATE_NAME=
export META_WHATSAPP_OTP_TEMPLATE_LANGUAGE=en_US
export FB_PAGE_ID=
export IG_BUSINESS_ACCOUNT_ID=
export META_GRAPH_VERSION=v23.0
export MARKETING_AI_ENABLED=true
export MARKETING_OPENAI_API_KEY=
export MARKETING_OPENAI_MODEL=gpt-4.1-mini
export MARKETING_OPENAI_IMAGE_MODEL=gpt-image-1.5
export MARKETING_OPENAI_IMAGE_SIZE=1024x1024
export MARKETING_OPENAI_IMAGE_QUALITY=medium
export MARKETING_META_ACCESS_TOKEN=
export MARKETING_INSTAGRAM_BUSINESS_ACCOUNT_ID=
export MARKETING_FACEBOOK_PAGE_ID=
export MARKETING_META_GRAPH_VERSION=v23.0
export MARKETING_WHATSAPP_PHONE_NUMBER_ID=
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
- `POST /api/marketing/campaigns`
- `POST /api/marketing/campaigns/{campaignId}/generate`
- `GET /api/marketing/campaigns`
- `GET /api/marketing/campaigns/{campaignId}`
- `PUT /api/marketing/content/{contentId}`
- `POST /api/marketing/content/{contentId}/approve`
- `POST /api/marketing/content/{contentId}/reject`
- `POST /api/marketing/content/{contentId}/schedule`
- `POST /api/marketing/content/{contentId}/publish-now`
- `GET /api/marketing/approval-queue`
- `GET /api/marketing/analytics`

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
- AI-generated marketing content never publishes without owner/admin approval
- Only approved marketing content can be scheduled or published
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
- Meta WhatsApp Cloud API requires a phone number id, permanent access token, customer opt-in, and approved templates for OTP/marketing outside the customer-service window
- Meta publishing requires Page and Instagram Business credentials with publish permissions
- `schema.sql` and `data.sql` bootstrap the database predictably

## Next Production Upgrades

- Keep Meta, OpenAI, Razorpay, and database secrets in AWS Secrets Manager
- Add richer per-recipient WhatsApp delivery tracking and template management
- Add audit tables and inventory movement ledger
- Add pagination and search endpoints
