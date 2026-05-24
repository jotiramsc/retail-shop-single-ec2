# CURRENT TASK

## Current Task: Billing UPI Payment, Reviews, Toasts, and Deployment

Status: Implemented, verified, deployed to EC2, and live-smoke verified.

Scope:
- Shop billing now has a Razorpay UPI payment flow for the `UPI` payment method.
- Billing reuses the existing Razorpay payment service and payment transaction linking instead of adding a duplicate integration.
- A shop billing payment order can be created from the current invoice preview, then finalized only after Razorpay success or verified payment status.
- Billing invoice creation accepts Razorpay order/payment/signature details and verifies them before saving a UPI invoice.
- Successful UPI billing links Razorpay diagnostics to the saved invoice/order and prevents unverified UPI invoices from reducing stock.
- UPI billing shows a modal with amount, customer, mobile, payment status, retry/regenerate QR, change-to-cash, and post-success Print/Download/WhatsApp actions.
- Existing website Razorpay checkout remains unchanged.
- Public storefront reviews moved to the end of the page.
- Review submission requires only a visitor mobile number; name, city, product/category, and comment are optional.
- Review mobile input and API handling normalize Indian mobile numbers before saving.
- Review moderation uses the shared Sneat-style toast host for show/hide/delete feedback.
- Theme color customization UI was disabled so the approved Krishnai storefront/admin look stays fixed.
- Billing existing-customer status was made compact; it now shows only the link/status instead of a large customer detail panel.

Verification:
- Frontend `npm run build` passed.
- Backend `./mvnw -q -DskipTests package` passed.
- Backend focused tests passed:
  - `BillingServiceImplTest`
  - `CustomerAuthServiceImplTest`
  - `CustomerServiceImplTest`

Deployment:
- Release `local-20260524164000` packaged and uploaded to `s3://retail-shop-single-artifactbucket-x5x2zrjqspuk/releases/local-20260524164000/release.tar.gz`.
- EC2 SSM deploy command `017449eb-5bb9-4711-83f8-1c4f2c4750a7` completed successfully.
- Live `https://kpskrishnai.com/actuator/health` returned `UP`.
- Live `/account`, `/app`, `/app/billing`, and `/app/crm/reviews` returned HTTP 200.
