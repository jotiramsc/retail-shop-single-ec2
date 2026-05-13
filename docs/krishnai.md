# Project Context - 2026-05-12

## Overview

This repository has evolved from the original "retail POS for cosmetics and jewellery" request into a broader retail platform with two major surfaces:

1. An internal staff/admin application for store operations
2. A public-facing ecommerce/storefront application for customers

The current working product name in the admin React app is `Luxe Retail Studio`, but branding is dynamic and driven by receipt/public config data.

## Main Stack

- Backend: Java 17, Spring Boot 3.3.5, Spring Web, Validation, JPA, Security, Actuator, Redis support
- Frontend: React 18, Vite, React Router, Axios
- Database: PostgreSQL primary target, H2 supported for local fallback
- Infra: Docker Compose for local multi-container runtime, single-container Dockerfile for deploy, EC2/App Runner oriented docs and scripts
- Optional integrations: Meta WhatsApp Cloud API, Meta Graph API, Razorpay, AWS S3, AWS Secrets Manager, Redis, Google auth, Google Maps

## Runtime Shape

### Local docker-compose

Defined in `docker-compose.yml`.

- PostgreSQL: host port `5434`
- Backend: host port `8084`
- Frontend: host port `5176`

### Single-container deployment

Defined by the root `Dockerfile`.

- Builds the Vite frontend
- Copies frontend `dist` into Spring Boot static resources
- Packages one backend jar that serves API + UI

### Backend configuration

Primary runtime config is in `backend/src/main/resources/application.yml`.

Important groups already present:

- datasource and SQL init
- CORS allowlist
- staff JWT auth
- customer auth and OTP policy
- pricing and delivery config
- Redis toggles
- AWS S3 and Secrets Manager
- Razorpay payment settings
- Meta WhatsApp and Graph API settings
- Google auth/maps settings
- marketing AI settings
- omnichannel website/webhook settings

## High-level Product Surfaces

### 1. Internal retail console

Mounted under `/app/*` in `frontend/src/App.jsx`.

Main areas:

- Billing / POS
- Inventory
- Customers
- Campaign Studio
- Reports
- Salesperson sales
- Site interaction dashboard
- Brand / receipt configuration
- User management

### 2. Public storefront

Mounted on public routes in `frontend/src/App.jsx`.

Main areas:

- Home page
- Product listing
- Cart
- Checkout
- Orders
- Customer profile
- Wishlist
- Privacy policy

### 3. Secondary Next.js storefront prototype

There is also a separate `glowjewels/` app.

This is a standalone Next.js 15 TypeScript storefront/marketing frontend with its own assets and build system. It appears to be a parallel or exploratory frontend track rather than the main runtime currently wired through the Spring Boot + Vite path.

## Backend Module Map

### Core operational modules

- `BillingController.java` / `BillingServiceImpl.java`
- `ProductController.java` / `ProductServiceImpl.java`
- `CustomerController.java` / `CustomerServiceImpl.java`
- `OfferController.java` / `OfferServiceImpl.java`
- `ReportController.java` / `ReportServiceImpl.java`
- `ReceiptSettingsController.java` / `ReceiptSettingsServiceImpl.java`
- `StaffUserController.java` / `StaffUserServiceImpl.java`

### Ecommerce/storefront modules

- `CartController.java` / `CartServiceImpl.java`
- `CheckoutController.java` / `CheckoutServiceImpl.java`
- `OrderController.java` / `OrderServiceImpl.java`
- `AddressController.java` / `AddressServiceImpl.java`
- `WishlistController.java`
- `CustomerProfileController.java` / `CustomerProfileServiceImpl.java`
- `AuthController.java` / `CustomerAuthServiceImpl.java`

### Marketing and automation modules

- `CampaignController.java` / `CampaignServiceImpl.java`
- `MarketingAutomationController.java` / `MarketingAutomationServiceImpl.java`
- `AutomationServiceImpl.java`
- `AIContentGenerationServiceImpl.java`
- `SocialMediaServiceImpl.java`
- `FacebookPublisher.java`
- `InstagramPublisher.java`
- `WhatsAppPublisher.java`

### Omnichannel and conversational modules

- `OmnichannelCommerceController.java` / `OmnichannelCommerceServiceImpl.java`
- `WhatsAppSalesBotController.java` / `WhatsAppSalesBotServiceImpl.java`
- `SiteInteractionController.java` / `SiteInteractionServiceImpl.java`
- `GeoLookupService` via `IpapiGeoLookupService.java`

### Payments and media modules

- `PaymentWebhookController.java`
- `PaymentTransactionController.java`
- `RazorpayPaymentService.java`
- `ImageUploadController.java` / `S3ImageUploadService.java`
- `ImageMigrationController.java` / `ImageMigrationServiceImpl.java`
- `ImageProxyController.java`

### Cross-cutting support

- pricing engine under `service/pricing/`
- Redis state support in `service/support/RedisStateStore.java`
- global exception handling in `exception/`
- security and auth config in `config/` and `security/`

## Database Model Summary

Current schema is much broader than the original invoice/product/customer set.

Major tables defined or evolved in `backend/src/main/resources/schema.sql`:

- `products`
- `product_categories`
- `image_assets`
- `customers`
- `customer_otps`
- `site_visits`
- `cart`
- `cart_items`
- `wishlist_items`
- `addresses`
- `orders`
- `order_items`
- `payment_transactions`
- `offers`
- `invoices`
- `invoice_items`
- `campaigns`
- `campaign_content`
- `campaign_logs`
- `campaign_analytics`
- `approval_history`
- `publish_logs`
- `receipt_settings`
- `staff_users`
- `omnichannel_leads`
- `omnichannel_conversations`
- `omnichannel_conversation_messages`
- `social_webhook_events`
- `ai_recommendation_logs`

Notable product/storefront additions:

- `website_price_percentage`
- `website_price`
- multiple storefront visibility flags such as `show_in_editors_picks`, `show_in_new_release`, `show_in_shop_collection`, and related booleans
- `image_data_url` on products
- low-stock threshold per product

## Frontend Structure Summary

### Admin pages

Files under `frontend/src/pages/`:

- `BillingPage.jsx`
- `ProductsPage.jsx`
- `CustomersPage.jsx`
- `CampaignsPage.jsx`
- `ReportsPage.jsx`
- `SalespersonSalesPage.jsx`
- `SiteInteractionsPage.jsx`
- `ReceiptSettingsPage.jsx`
- `UsersPage.jsx`

### Public pages

- `PublicHomePage.jsx`
- `PublicProductsPage.jsx`
- `CartPage.jsx`
- `CheckoutPage.jsx`
- `OrdersPage.jsx`
- `CustomerLoginPage.jsx`
- `CustomerProfilePage.jsx`
- `WishlistPage.jsx`
- `PrivacyPolicyPage.jsx`

### Frontend API contract

Central browser API wrapper lives in `frontend/src/services/retailService.js`.

It already covers:

- staff auth and customer auth
- OTP and Google login
- site interaction tracking
- product image upload
- public catalog and trending catalog
- cart and wishlist
- coupon and checkout
- payment order creation and payment status
- address book
- customer profile
- website order placement and history
- admin products and product categories
- customers and customer history
- invoice preview/create/update/search
- offers create/update/delete
- marketing campaigns, approval queue, analytics
- daily reports, report invoice feed, low stock
- salesperson reporting
- receipt settings
- staff user CRUD

## Important Business Capabilities Already Implemented

### POS / billing

- invoice preview before save
- create and update invoice
- stock validation
- best-offer application
- manual discount with `FIXED` or `PERCENT`
- invoice search and edit flow
- receipt settings driven print output
- salesperson linkage

### Inventory

- product create/update/delete
- product image selection/upload
- low-stock thresholds
- restock/edit search UX
- storefront merchandising flags and website pricing fields

### Customer and auth

- staff login
- customer OTP login
- customer Google login support
- profile editing
- address management
- order history
- wishlist

### Ecommerce

- public product catalog
- cart merge/update
- coupon application
- checkout quote/payment order
- order placement and order tracking

### Marketing and automation

- campaign CRUD
- AI-assisted draft generation
- owner/admin approval flow
- scheduling and publish-now actions
- WhatsApp/Instagram/Facebook publishing abstractions
- analytics and approval queue

### Omnichannel

- lead capture
- product search for conversational/sales workflows
- webhook handling
- website deep links with auto-add and coupon support
- site visit capture and geo-enriched interaction reporting

## External Integrations Present In Code/Config

### Meta WhatsApp Cloud API

Configured via `app.meta.*` and `marketing.whatsapp.*`.

Used for:

- OTP delivery
- marketing messages
- bot responses
- template catalog/send paths

### Meta Graph API

Configured via `app.meta.*` and `marketing.meta.*`.

Used for:

- Instagram publishing
- Facebook publishing
- page token refresh support

### Razorpay

Configured via `app.razorpay.*`.

Used for:

- payment order creation
- payment transaction tracking
- webhook validation/processing

### AWS

Configured via `app.aws.*`.

Used for:

- S3 image storage
- CloudFront URL serving
- Secrets Manager integration support

### Redis

Configured via `app.redis.*`.

Used optionally for:

- OTP state
- cart state

## Docs Already In Repo

Useful existing references:

- `README.md`
- `docs/single-ec2-deployment.md`
- `docs/github-single-ec2-deploy.md`
- `docs/security-hardening-plan.md`
- `docs/marketing-automation-setup.md`
- `docs/omnichannel-commerce-configuration.md`
- `docs/s3-cloudfront-image-upload.md`
- `docs/whatsapp-test-account-setup.md`
- `docs/customer-module-design.md`
- `docs/aws-secrets-migration-inventory.md`

## Current State / In-progress Signals

The worktree is currently dirty and the codebase is actively evolving. Based on the current diff, these areas appear to be in motion:

- migration from older providers to `Meta` and `Razorpay`
- ongoing expansion of customer auth/profile flows
- public storefront and omnichannel commerce improvements
- deployment hardening and AWS secret handling
- marketing automation enhancements

Specific signals:

- older services such as `GupshupOtpDeliveryService.java` and `PhonePePaymentService.java` were removed
- newer services such as `RazorpayPaymentService.java` and `MetaPageTokenRefreshService.java` were added
- new controllers exist for wishlist, privacy policy, payment transactions, and WhatsApp templates
- infrastructure templates and EC2 bootstrap scripts are being revised

## Practical Mental Model

The best way to think about the repository today is:

- store operations system
- ecommerce website
- social/WhatsApp commerce bridge
- marketing automation workspace
- AWS-ready deployment package

It is not just a billing app anymore.

## Key Risks / Things To Watch

- There are effectively two frontend directions: `frontend/` and `glowjewels/`
- The repository has a large uncommitted change surface, so any new feature work should be careful around adjacent files
- The database schema has grown significantly; future changes should keep schema docs and seed strategy aligned
- Deployment docs exist for multiple paths, so the chosen production path should be made explicit before cutover
- A consolidated living architecture doc was missing before this file was added

## Recommended Next Reference Points

If continuing work, start with these files:

- `README.md`
- `backend/src/main/resources/application.yml`
- `frontend/src/App.jsx`
- `frontend/src/services/retailService.js`
- `backend/src/main/resources/schema.sql`
- `docs/marketing-automation-setup.md`
- `docs/omnichannel-commerce-configuration.md`

