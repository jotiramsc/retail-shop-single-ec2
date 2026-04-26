# Retail Shop Context - 2026-04-25

> Update on 2026-04-25: PhonePe is now wired in source and deployed on ECS. The backend uses `PhonePePaymentService`, the checkout page redirects to PhonePe and completes the order after the redirect back to `/checkout`, and ECS is running backend task definition `:17` plus frontend task definition `:13`.

## Current architecture

- Frontend: React/Vite customer site + admin app
- Backend: Spring Boot
- Database: PostgreSQL
- Images: S3 + CloudFront path has already been introduced for product media
- Deployment:
  - Frontend ECS service: `retail-shop-cluster-retail-shop-frontend-ecs`
  - Backend ECS service: `retail-shop-cluster-retail-shop-backend-ecs`

## Current functional status

### Admin / back-office

- Billing, inventory, offers, reports, receipt settings, and user management pages exist.
- Product/category/offer/admin-side flows have been extended multiple times.
- Reports were updated to include both billing orders and website orders.

### Customer site

- Product listing and detail/catalog flows exist.
- Guest cart exists in frontend storage.
- Customer OTP login flow exists.
- Customer profile page exists with:
  - profile view/update
  - address list
  - logout
- Cart, checkout, and orders pages exist.

## Customer auth status

- OTP login is currently app-level custom logic.
- In dev/local mode the OTP is intentionally returned to the screen for easier testing.
- MSG91-ready backend support was added previously, but Firebase phone auth is not implemented in source.

## Payment status

### Important

PhonePe is **not integrated yet** in application code.

The live/source code is still wired to **Razorpay**.

### Evidence in source

- Backend payment service:
  - `backend/src/main/java/com/retailshop/service/impl/RazorpayPaymentService.java`
- Backend config:
  - `backend/src/main/resources/application.yml`
- Frontend checkout flow:
  - `frontend/src/pages/CheckoutPage.jsx`

### What the code currently does

- Backend creates orders against `https://api.razorpay.com/v1/orders`
- Backend verifies Razorpay signature fields:
  - `razorpayOrderId`
  - `razorpayPaymentId`
  - `razorpaySignature`
- Frontend loads:
  - `https://checkout.razorpay.com/v1/checkout.js`
- Frontend opens `window.Razorpay`

### What exists on ECS

PhonePe environment variables were added to the backend ECS task definition, including:

- `PHONEPE_CLIENT_ID`
- `PHONEPE_CLIENT_SECRET`
- `PHONEPE_CLIENT_VERSION`
- `PHONEPE_ENV=sandbox`
- `PHONEPE_REDIRECT_URL`
- `PHONEPE_WEBHOOK_URL`

However, those variables are not used by the current source code because no PhonePe service/controller/config implementation exists yet.

## Why PhonePe is not working

Because there is a **code/config mismatch**:

- ECS has PhonePe variables
- Source code still uses Razorpay only

So the website cannot perform PhonePe checkout until the payment flow is actually rewritten or extended in source.

## Current payment-related gaps

To make PhonePe work end to end, these areas still need code changes:

1. Add backend PhonePe configuration binding in `AppProperties`
2. Add PhonePe section in `application.yml`
3. Implement a `PhonePePaymentService`
4. Update checkout/payment-order APIs to return PhonePe-compatible checkout payload
5. Replace Razorpay-specific request fields in order placement
6. Add webhook/callback handling for PhonePe
7. Update frontend checkout page to use PhonePe flow instead of `window.Razorpay`
8. Update UI text that still mentions Razorpay

## ECS notes

- PhonePe variables are already present on ECS backend task definitions from earlier work.
- The frontend hostname has been intermittently unstable before, but that is separate from the missing PhonePe code.
- Earlier ECS rollout issues were also caused by image/platform mismatch until images were rebuilt for `linux/amd64`.

## Important source files

### Payment

- `backend/src/main/java/com/retailshop/service/PaymentService.java`
- `backend/src/main/java/com/retailshop/service/impl/RazorpayPaymentService.java`
- `backend/src/main/java/com/retailshop/controller/CheckoutController.java`
- `backend/src/main/java/com/retailshop/controller/PaymentWebhookController.java`
- `backend/src/main/java/com/retailshop/service/impl/OrderServiceImpl.java`
- `frontend/src/pages/CheckoutPage.jsx`

### Customer account

- `frontend/src/components/CustomerAccountMenu.jsx`
- `frontend/src/pages/CustomerProfilePage.jsx`
- `backend/src/main/java/com/retailshop/controller/CustomerProfileController.java`
- `backend/src/main/java/com/retailshop/service/impl/CustomerProfileServiceImpl.java`

## Recommended next step

Do not debug PhonePe at the dashboard level yet. First replace the existing Razorpay implementation in code with PhonePe integration, then test locally, and only after that redeploy to ECS.
