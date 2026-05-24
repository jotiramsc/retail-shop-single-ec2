# Krishnai App Context

Last updated: 2026-05-24

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

## Functional State Added On 2026-05-24

This section captures the latest implementation context and should be updated after every feature change.

### Admin Shell And Navigation

- Admin routes live under `/app/*` in `frontend/src/App.jsx`.
- Header shows signed-in user avatar, display name, role, and logout.
- Parent menu text opens the first child route; the icon button expands/collapses submenus.
- Collapsed menu must not render floating submenu content in the page center.
- CRM now includes a `Reviews & Ratings` submenu at `/app/crm/reviews`.
- Theme variables from Brand Configuration are applied to the admin shell through CSS variables:
  - `--kps-admin-primary`
  - `--kps-admin-accent`
  - `--kps-admin-surface`
  - `--kps-admin-text`

### Mobile Number Normalization

Mobile numbers must be accepted and stored as 10 digits.

Rules:

- Strip non-digits.
- If the result is 11 digits and starts with `0`, remove that leading `0`.
- If the result is 12 digits and starts with `91`, remove the country code.
- If the result is 13 digits and starts with `091`, remove that prefix.
- Do not strip `91` from an already-valid 10-digit number because some valid numbers can start with `91`.

Implementation:

- Backend utility: `backend/src/main/java/com/retailshop/util/MobileNumberUtils.java`
- Frontend utility: `frontend/src/utils/mobile.js`
- Frontend validation delegates to this utility in `frontend/src/utils/validation.js`.
- Customer profile and billing checkout use normalized mobile values before save/API calls.

### Admin Delete Flows

Admin/owner destructive operations now include:

- Customers: `DELETE /api/customers/{customerId}`
- Website orders: `DELETE /api/admin/orders/{orderId}`
- Invoices: `DELETE /api/billing/{id}`
- Existing product/offer/campaign delete APIs remain admin-controlled where already implemented.

Stock behavior:

- Website order delete restores product stock from order items.
- Invoice delete restores product stock from invoice items, deletes invoice items, and deletes the linked billing order.
- Customer delete removes invoices/orders and restores stock once for the relevant sale records.

Frontend services:

- `retailService.deleteCustomer`
- `retailService.deleteOrder`
- `retailService.deleteInvoice`

UI:

- Admin/owner delete buttons appear in order/invoice lists.
- Admin/owner delete button appears in the CRM customer list.

### Reviews And Ratings

The customer-facing “Worn & Loved” section is now data-backed.

Backend:

- Entity: `CustomerReview`
- DTOs:
  - `CustomerReviewRequest`
  - `CustomerReviewResponse`
  - `CustomerReviewModerationRequest`
- Repository: `CustomerReviewRepository`
- Service: `CustomerReviewService`
- Controller: `CustomerReviewController`
- Schema table: `customer_reviews`

Public APIs:

- `GET /api/reviews/public`
- `POST /api/reviews`

Admin APIs:

- `GET /api/reviews/admin`
- `PATCH /api/reviews/admin/{id}`
- `DELETE /api/reviews/admin/{id}` admin/owner only

Frontend:

- `PublicHomePage` loads approved reviews into “Worn & Loved”.
- Visitors can submit reviews from the storefront; submitted reviews are hidden until approved.
- `CustomerReviewsPage` lets staff approve/hide reviews and admin/owner delete them.

### Theme Customizer

Theme customization lives on Brand Configuration → Theme and media.

Backend receipt settings now include:

- Website:
  - `websitePrimaryColor`
  - `websiteAccentColor`
  - `websiteSurfaceColor`
  - `websiteTextColor`
  - `websiteCornerRadius`
  - `websiteButtonStyle`
  - `websiteDensity`
- Admin:
  - `adminPrimaryColor`
  - `adminAccentColor`
  - `adminSurfaceColor`
  - `adminTextColor`
  - `adminSidebarStyle`
  - `adminHeaderCompact`

Frontend:

- `ReceiptSettingsPage` exposes color pickers/selects and previews for website and admin panel themes.
- `frontend/src/utils/branding.js` normalizes theme settings.
- `App.jsx` applies admin theme variables to the protected shell.
- `PublicHomePage.jsx` applies website theme variables to the storefront.

### Responsive And UI Cleanup

Global styling in `frontend/src/styles/global.css` now includes:

- Mobile grid collapse for settings, CRM, marketing, customer detail, review form, and table-heavy areas.
- Sneat-colored pagination hover/disabled states.
- Cleaner white admin panels replacing older beige/gold info panels where touched.
- Mobile-safe CRM customer rows with truncation.
- Review form styling and storefront theme variable support.

### Category Icon Generation Context

Known category icon generation failure path was:

- Marketing/OpenAI image config missing.
- Code attempted fallback upload through `imageUploadService.uploadImageBytes(...)`.
- Test expected no upload when config is missing.

Current desired behavior:

- Missing AI image config should fail fast with a clear message and no repeated fallback upload attempts.
- AI failure with config present can fall back.
- Fallback upload failure should surface a clear error.
- Category icons should be transparent icon-only assets with no background tile, no border, and no curved decorative lines.
- Users should be able to choose icon colors before generation.

Backend test run currently passes, but category icon fallback tests log expected warnings.

### Campaign Studio Context

Owner requirements still active for future verification:

- Templates page should list complete ready-made campaign templates with generated image, text, and layout.
- Create/edit campaign pages should not list every template; selected template should open for use/editing.
- User can edit selected fields and regenerate templates/images.
- Remove approve/reject from campaign content UI where only “Schedule” and “Publish now” should remain.
- Schedule should ask date/time in a popup.
- Image URL should be read-only/view-only.
- Recent marketing work should be clickable.
- Create campaign should land on campaign view.
- Campaign dashboard should show engagement when available: likes, comments, WhatsApp delivered.

### Customer Website Context

Routes:

- `/`
- `/products`
- `/product/:productId`
- `/cart`
- `/checkout`
- `/orders`
- `/account`

Profile/account expectations:

- Wizard-like customer profile form with icons.
- Save any step at any time.
- Profile image upload.
- Google login should use Google profile image when available.
- Mobile save/update must use the 10-digit normalization rules above.

### Billing And Orders Context

- Billing page product picker should be above checkout on mobile.
- Checkout quantity input must stay visible on mobile.
- Staff with checkout access should be able to perform checkout operations.
- Invoice edit should open `/app/billing?editInvoiceId=...` and hydrate checkout inputs/cart.
- Order details customer name/mobile should open CRM customer details.
- Coupon code should link to related offer/coupon details/search.
- Website order status can be changed from admin order list/details.
- Shop order edit opens checkout.

### CRM Context

CRM customer overview should remain a combined tabular single-page view:

- Customer summary.
- Orders.
- Addresses.
- Preferences.
- Search/login/activity history.
- Support chat link.
- AI/engagement insights.
- Reviews & Ratings moderation.

Customer list rows and dashboard customer cards should navigate to customer details.

### Product And Category Context

Product list/add/edit:

- Keep Sneat eCommerce-inspired layout.
- Preserve all functional fields: image upload, category, billing/website flags, stock, pricing, visibility controls.
- Product addition should remain simple and mobile-friendly.

Category list/add/edit:

- Use Sneat category list layout.
- Add/edit via right-side drawer.
- Keep category icon generation after transparent icon-only fixes.

### Dashboard Context

Admin dashboard should auto-populate and link cards to relevant pages:

- Total customers.
- Customer visits.
- Total sales.
- Total orders.
- Pending/completed/cancelled orders.
- Revenue today/month.
- Low-stock products.
- Top-selling products.
- Recent orders.

If staff lacks dashboard analytics permission, do not make the page mostly “Access denied”; show only modules the user can access.

### Latest Verification

Run on 2026-05-24:

```bash
cd backend
./mvnw test -q
```

Result: passed. Category icon fallback tests logged expected warnings.

```bash
cd frontend
npm run build
```

Result: passed. Vite chunk-size warning remains informational.
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

## Billing UPI, Reviews, and Toast Context

- Shop billing supports Razorpay UPI for in-store billing while preserving the existing website Razorpay flow.
- The billing frontend creates a billing payment order from the current invoice draft and opens Razorpay with UPI enabled.
- The UPI modal shows amount, customer, normalized mobile, payment status, retry/regenerate QR, change-to-cash, and success actions for Print, Download, and WhatsApp invoice.
- Backend billing invoice create/update accepts optional Razorpay payment fields and verifies UPI payments before invoice save.
- Unverified/failed/closed UPI payments do not finalize the bill and do not reduce stock.
- Successful billing UPI payments link Razorpay diagnostics to the generated invoice/order.
- Storefront review capture now appears at the end of the public page.
- Review form requires only visitor mobile number; optional fields default safely when omitted.
- Review mobile values normalize through the shared Indian mobile normalization behavior before persistence.
- Admin review moderation uses the shared `ToastHost` instead of inline-only success messages.
- Theme color customization controls were removed from the visible Theme and Media screen so the approved Krishnai theme remains fixed.
- Billing customer detection copy is compact; the checkout no longer shows a bulky existing-customer details panel.

Verification run on 2026-05-24 after billing UPI/review changes:

```bash
cd frontend
npm run build
```

Result: passed. Vite chunk-size warning remains informational.

```bash
cd backend
./mvnw -q -DskipTests package
./mvnw -q -Dtest=BillingServiceImplTest,CustomerAuthServiceImplTest,CustomerServiceImplTest test
```

Result: passed.

## Customer Verification And Billing-Created Accounts

Customer accounts captured from billing are reusable customer records, but they are not website-login accounts until OTP activation completes.

Backend customer state:

- `verificationStatus`: `VERIFIED` or `UNVERIFIED`.
- `customerSource`: `BILLING`, `WEBSITE_SIGNUP`, `ADMIN_CREATED`, or legacy `BOTH`.
- `loginEnabled`: customer website access gate.
- `otpVerifiedAt`: timestamp set when OTP activation succeeds.
- `lastOrderAt`: updated when a billing invoice/order or website order is created.
- `mobileVerified` is retained for compatibility and maps to `verificationStatus=VERIFIED`.

Billing behavior:

- Billing uses `CustomerService.findOrCreateCustomer(...)`.
- Mobile numbers are normalized through `MobileNumberUtils.normalizeIndianMobile(...)`; valid 10-digit numbers beginning with `91` are preserved.
- New billing customers are saved as `source=BILLING`, `verificationStatus=UNVERIFIED`, `loginEnabled=false`, `mobileVerified=false`.
- Existing customers are reused by normalized mobile.
- Verified customer names are not overwritten automatically from billing.
- Missing safe profile fields may be filled for unverified billing-created customers.

Signup and OTP login behavior:

- Signup with an existing unverified billing-created mobile sends OTP and returns:
  “An account already exists with this mobile number from your previous purchase. Please verify using OTP to activate your account.”
- Signup with an existing verified mobile does not create a duplicate and returns:
  “An account already exists with this mobile number. Please login using OTP.”
- Signup with a new mobile creates `source=WEBSITE_SIGNUP`, `verificationStatus=UNVERIFIED`, `loginEnabled=false`, then sends OTP.
- Login with an unknown mobile does not create a customer and returns:
  “No account found with this mobile number. Please sign up first.”
- Successful OTP verification sets `mobileVerified=true`, `verificationStatus=VERIFIED`, `loginEnabled=true`, and `otpVerifiedAt=now`, then issues the customer token.
- Customer profile and checkout APIs reject customers that are unverified or login-disabled, except OTP activation endpoints.

OTP security:

- OTPs are SHA-256 hashed with the customer auth secret before storage.
- OTP expiry uses `app.customer-auth.otp-ttl-minutes`, default 5 minutes.
- Resend cooldown uses `app.customer-auth.otp-rate-limit-seconds`.
- Verification attempt limits use `app.customer-auth.otp-max-attempts`.
- User-facing OTP errors are standardized:
  - `OTP has expired. Please request a new OTP.`
  - `Invalid OTP. Please try again.`

Admin and billing UI:

- Billing mobile lookup shows customer status: new customer, existing verified customer, or existing unverified customer.
- CRM customer list/details show verification, source, and login-enabled badges.
- CRM filters include verified, unverified, billing-created, and website-signup customers.

Verification run on 2026-05-24 after customer-auth changes:

```bash
cd backend
./mvnw test -q
```

Result: passed. Category icon fallback tests still log expected warning stack traces while passing.

```bash
cd frontend
npm run build
```

Result: passed. Vite chunk-size warning remains informational.
