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
- `show_on_website` controls public storefront visibility and defaults true
- `use_for_billing` controls local shop billing visibility and defaults true
- multiple storefront visibility flags such as `show_in_editors_picks`, `show_in_new_release`, `show_in_shop_collection`, and related booleans
- `image_data_url` on products
- low-stock threshold per product

Product visibility rules:

- Public storefront catalog, home, trending, and product detail APIs return only `showOnWebsite=true` products.
- Billing/POS product selectors use only `useForBilling=true` products.

Product images and pricing:

- Product multiple-image sliders use ordered S3/CloudFront URL references stored on the product; image binaries are uploaded through the existing S3 image upload flow and are not stored in backend entities.
- The first image URL is the primary/default image; old single-image products continue to work.
- Website pricing surfaces display active offer/coupon deals with original price, offer price, discount percent, savings, coupon/offer badges, and free-delivery labels when applicable.
- Customer listing product cards intentionally stay minimal: they show only final offer price, category, product name, availability, image, View details, and Add to cart.
- Customer product cards show a clean deal stack only when applicable: small struck original price, one discount percent badge, final price, availability, image slider, View details, and Add to cart. Savings text, coupon chips, and extra badges are intentionally hidden from listing cards.
- Customer listing price filters and sorting use final offer price after product/category/coupon discount and max-discount cap rules.
- Cart and wishlist product image/name areas open the public product detail page while quantity/remove/action controls remain isolated.
- Public product detail includes product structured data, gallery navigation, WhatsApp enquiry, and a collapsed AI description section with read more. Generated AI description appears only in the formatted product guide section to avoid duplicated prose near the price.
- Checkout summary includes WhatsApp confirmation/support and delivery/payment trust copy.
- Customer-facing website WhatsApp links use `+91 8830461523` (`wa.me/918830461523`) for product enquiry and checkout support.
- Admin product form includes AI description status and a lightweight product preview before publish/update.
- Admin support product suggestions use the same active ecommerce offer/coupon deal values and WhatsApp support product messages include MRP, deal price, discount, savings, and coupon/offer when available.
- Support inbox keeps resolved chats as archives. Active and Archived tabs share conversation history; archive filters support date ranges and resolved chats can be reopened.
- Offer forms submit initialized defaults such as `type=PERCENT` and active status even when the dropdown is not manually changed.
- Campaign/offer WhatsApp promotion sending uses campaign media first, then linked product image, then category icon when media is available.
- Products can optionally generate a separate AI customer description asynchronously after save; creation/update stays successful if generation is pending or fails.
- Category icon generation creates AI/fallback transparent circular icons and runs as a write transaction so generated S3 icon assets can persist.

WhatsApp bot stability:

- WhatsApp bot active session context is persisted on `omnichannel_conversations.bot_session_json` and mirrored in memory for speed.
- Session context keeps last category, last min/max budget, last intent, last product, and shown product history so follow-up messages such as "green" or "show more" remain contextual after restart.
- Incoming WhatsApp message IDs are stored on `omnichannel_conversation_messages.external_message_id`; durable duplicate checks prevent reprocessing across app restarts.
- Conversation messages store `correlation_id`; bot logs emit `whatsapp_bot_trace` with correlationId, sessionId, leadId, messageId, intent/category, product count, send result, provider message id, and error.
- Same-mobile webhook processing is synchronized inside a running instance to reduce out-of-order state writes.

Billing GST:

- Shop billing uses the shared order pricing service, so configured CGST/SGST from Brand Configuration apply to billing invoices and mirrored billing orders. Website delivery fees remain website-only.

WhatsApp bot:

- Welcome/greeting replies send the configured banner once per session and a single interactive menu with Shop Products, Browse Categories, Today's Offers, Track Order, and Talk to Shop. Repeat greetings use a compact menu without resending the banner.
- Product search replies are image-first. Each product card shows visible numbering, customer-facing pricing, discount, product-specific description, availability, and per-product View Details/Add to Cart buttons. Internal product IDs are hidden from customers.
- Product image media is sent before action buttons, with a short action delay so WhatsApp renders photos first.
- Bot session JSON stores the visible product number to product ID mapping so `VIEW 1`/`ADD 1` actions resolve correctly after restarts.
- Product details resolve the selected product only and do not trigger a fresh search. Back to Products reuses the previous visible product mapping instead of starting fallback search. Suspicious jewellery prices under the configured sanity threshold are filtered from WhatsApp recommendations unless normalized catalog data supplies a valid price.
- Add to Cart replies use the selected product image plus cart total/open-cart caption and cart action buttons.
- Browse Categories uses the greeting/category image, first three dynamic categories as quick buttons, and remaining categories under View More.
- Interactive menu replies are deterministic and are not rewritten by the bot orchestrator, preventing duplicate or delayed menu text.

Billing configuration:

- Brand Configuration stores CGST %, SGST %, tax enabled flag, website delivery fee, delivery enabled flag, and free-delivery threshold.
- CGST/SGST are calculated from configuration for both website orders and shop billing invoices.
- Website delivery fee applies only to website/ecommerce checkout and becomes free when the discounted subtotal reaches the configured threshold.
- Orders and invoices persist CGST/SGST split, combined tax, delivery, discounts, and final totals.

WhatsApp greeting/menu:

- Greeting sends `/assets/krishnai-whatsapp-greeting.png` first.
- The greeting menu is a single WhatsApp list card with View Collections, Offers, Track Order, and Show More.

Category icons:

- Admin category icon generation uses the campaign/marketing OpenAI configuration, post-processes the result into a transparent circular PNG icon, uploads to S3/CloudFront, and persists the selected URL.
- Backend-generated and frontend-generated fallback category icons have been removed; missing OpenAI configuration or image generation failure now returns a clear admin error.
- Show More opens Talk to Shop, dynamic categories, My Cart, and Support in one secondary list.
- Product search/listing sends up to four product images first, then one product action list for Details, Add to Cart, and More Similar to keep message order clean.
- Category icon generation creates transparent circular premium jewelry/cosmetics icons and stores the selected S3/CloudFront URL for admin, website, and WhatsApp category surfaces where supported.

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
- WhatsApp bot category menus are dynamic: active `product_categories` drive category rows and labels, with live product categories as fallback.
- WhatsApp product cards must be filtered against the selected/requested category before sending images or list rows; mismatched search results are discarded and catalog fallback is used.
- WhatsApp welcome uses quick actions plus a Show More list; product responses avoid repeated shown products for the same category.
- WhatsApp welcome menu is text-first without a logo image, so quick buttons appear before collection/product content.
- WhatsApp product search sends a short intro first, then up to five matching product images, then quick actions: Details, Add to Cart, Yes show more. It does not send a separate collapsed View Products list card in product results.
- Product image captions include VIEW/ADD reply codes. The More flow paginates the next five products and stops with a no-more-products message.
- WhatsApp order tracking uses a text progress bar and lists being-delivered items in the status card.
- Products whose names clearly conflict with the selected category are rejected even if stored under that category, preventing bad admin data from leaking into WhatsApp cards.
- Source-tracked product links use public SPA route `/product/{productId}?campaignId={campaignId}&source={source}`; the storefront records valid campaign/source visits before showing the product.
- Campaign Studio create form shows validation/API errors next to the Save buttons and scrolls them into view, so blocked submissions are visible without checking Network.
- Campaign Studio WhatsApp publishing sends each unique customer mobile through the same Gupshup/WhatsApp sender path used by support messages. It stores compact per-number sent/failed delivery reports in publish logs and surfaces the latest report on WhatsApp content cards.
- WhatsApp support inbox is single-agent and reuses omnichannel conversation/message tables. Admins can view unread/open chats, reply to WhatsApp, mark resolved, and send inventory products from `/app/support`. The first implementation uses REST polling instead of WebSocket.
- Support product suggestions search existing website-visible inventory by name, category, SKU, and simple price filters. Product suggestions are sent through the existing WhatsApp sender with image when publicly available, View Product link, Add to Cart link, support-team note, and structured conversation metadata for product/status history.
- Storefront route `/cart/add?productId={id}&source=whatsapp-support` adds the product to the signed-in customer cart or guest cart, then opens `/cart`.
- Support chat has Enter-to-send, Shift+Enter newline, auto-scroll, loading states, unread badge polling, and a global admin popup when unread WhatsApp support messages arrive outside `/app/support`.
- Customer signup by OTP asks only for name after OTP verification and allows immediate skip/continue; OTP validation accepts 4-8 numeric digits.
- Admin customer details show name, mobile, email, latest address, total orders, pending orders, total spent, order history, and purchase history.
- Customer Intelligence / Customer CRM is a spacious green/gold customer 360 workspace with a left customer list and right-side overview, timeline, orders, preferences, search activity, login history, support chat, AI insights, and notes.
- Customer search dropdowns can return recent customers before typing, and customer search/filter/product/cart/wishlist/order/support activity is captured into CRM intelligence where a customer session exists.
- CRM support chat starts with the Krishnai greeting and a conversational onboarding flow for birthday, anniversary, language, favorite categories, budget, and shopping interests instead of showing a large static form first.
- Customer intelligence derives sentiment, purchase prediction, churn risk, engagement score, smart segments, high-value badge, and recommended product/category signals from profile, order, login, and activity data.
- Product categories can store `icon_image_url`; Inventory generates an OpenAI category icon through the marketing/campaign OpenAI configuration and stores the selected CloudFront image URL for website and WhatsApp category surfaces.
- Reports with salesperson filter `WEBSITE`/`Website` show pending website orders first; if none exist, they show today's website orders. Delivered/completed/cancelled/returned orders are excluded from the pending-first view, and the Reports UI labels this as the website order priority queue.

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

## Latest Billing UPI / Reviews Update

- Shop billing supports Razorpay UPI payments without duplicating the existing Razorpay website checkout integration.
- `POST /api/billing/payment-order` creates a payment order from a billing invoice preview for authorized billing users.
- `GET /api/billing/payment-status` reuses the existing payment status logic so a late webhook/status update can still finalize safely.
- Billing invoice create/update accepts optional Razorpay order/payment/signature fields. UPI invoices are rejected unless payment is verified by signature or successful payment status.
- On UPI success, billing links payment diagnostics to the invoice/order, marks the invoice paid through the existing billing save flow, and relies on existing idempotent stock deduction behavior.
- Billing UI shows a compact UPI modal with payment state, retry/regenerate QR, change-to-cash, and post-success Print/Download/WhatsApp actions.
- Storefront reviews are collected at the end of the page. Only mobile number is required; name, city, product/category, and comment are optional.
- Customer review mobile numbers are normalized on the frontend and backend before save.
- Admin review moderation uses shared Sneat-style toasts for show/hide/delete feedback.
- Theme color customization UI is disabled so the approved Krishnai storefront/admin styling remains stable.

## Latest Production Decisions

- Customer mobile is treated as the shared identity for billing and website accounts. Backend lookups compare the last 10 normalized digits so `+91`, `91`, and 10-digit forms resolve to the same customer; billing-created customers remain valid customer profiles and website verification marks them as website/both source without blocking browsing.
- WhatsApp welcome now sends the configured greeting banner and one menu list only: Browse Categories, My Cart, Track Orders, Talk to Agent, Support. Category/product/order flows use dynamic DB data and session context before falling back.
- WhatsApp order tracking focuses on in-progress orders, excludes completed/delivered/cancelled failures, and shows a 7-step progress tracker: Placed, Confirmed, Processing, Packed, Shipped, Out For Delivery, Delivered.
- Public storefront category navigation and product filters show the saved category icon as a small rounded visual chip when one is configured.
- Product category icon generation is OpenAI-only, uses the campaign/marketing OpenAI configuration, uploads successful transparent circular PNG icons to CloudFront, and surfaces configuration/generation failures to the admin instead of returning generated fallbacks.
- Customer CRM is now a green/gold customer intelligence workspace with a 30/70 customer-list/detail layout, spacious tabs, timeline, search activity, login history, preferences, support chat, AI insights, notes, richer storefront event tracking, and conversational onboarding instead of first showing a large static profile form. EC2 release `local-20260521215711` is live after additive CRM tracking columns were applied.
- Website order reports use the `WEBSITE` salesperson filter to show all pending website orders across dates first, then today's website orders when there are no pending website orders.
- Billing invoices now surface configured CGST/SGST in the admin checkout preview, latest invoice summary, printed receipt, and billing-backed customer order records.
- Inventory product create auto-fills SKU from the first three product-name characters and auto-fills shop/website pricing from cost price until the user edits those price fields.
- Inventory product delete is a soft delete: products are marked inactive and hidden from website, billing, homepage, search, and merchandising surfaces while historical receipts/invoices keep their product references.
- Admin dashboard UI is being compacted for faster shop work: reduced non-billing headers, tabbed Inventory with separate Products/Categories views, compact product table with visibility toggles, clearer Offer targeting/schedule controls, Campaign one-click approve/publish shortcuts, embedded Offers builder without suggestions, tighter Billing checkout space, and Site Interaction overflow guards.
- Offer creation supports sorted store/category/product targeting with target selectors grouped directly under "Offer applies to", date-based scheduling fields grouped under "Offer schedule" without time pickers, and Buy One Get One / Buy X Get Y / Combo metadata; same-product Buy/Get rewards can contribute to line discount calculation.
- Offer schedules default to a one-month period for Date Range, Specific Days, and Weekend Only when dates are empty. Weekend offers automatically use Saturday/Sunday, Specific Days requires at least one selected weekday, Always Active omits invalid blank date fields, and coupon codes auto-generate as unique six-character uppercase codes from the offer name.
- Category icons are stored as a single active URL on the category. Generate creates one icon when missing; Regenerate replaces the existing icon reference so duplicate category icon records are not created.
- Backend writes rolling application logs to `${APP_LOG_FILE:/tmp/retail-shop/retail-shop.log}` with configurable size/history through `APP_LOG_*` environment variables.
- Facebook Catalog sync is configured only inside Brand Configuration > Facebook Catalog. Brand config stores enablement, Meta Pixel ID, feed token, and last generated time. Active website-visible products and active categories sync by default, missing Facebook category fields are auto-mapped from category names, and admin can override per product/category when needed. Public XML/CSV feeds are read-only, token-protected when a token is configured, and include only active website-visible in-stock products with public images, mapped Facebook categories, gallery `additional_image_link` values, inventory, category labels, offer labels, and website source labels. Public product pages emit Meta item identifiers in OpenGraph plus Product JSON-LD and Meta Pixel ViewContent/AddToCart; checkout emits Purchase. Catalog add-to-cart links can use `/cart/add?product={sku}&qty=1&source=whatsapp&campaign={campaignName}` or the older `productId` form; the storefront resolves SKU links and adds to customer or guest cart.
- Sneat admin migration now favors a cleaner, flatter navigation: Billing is a single workflow, Inventory keeps Products and Categories, Customer CRM keeps only the essential intelligence screens, Support replaces the older WhatsApp admin/debug menus, Campaign Studio keeps active campaign workflows only, and duplicate report/sales/site/users submenus were removed. The sidebar auto-collapses unrelated submenus so only one menu remains expanded, and UI settings/options were removed to keep the shell simple.
- Campaign image generation was aligned with the stable category icon approach: OpenAI-generated image bytes are uploaded directly to CloudFront-backed `marketing-campaigns` storage with the returned content type and a bounded timeout, avoiding the extra Java composition/overlay step that was more likely to fail than the category icon flow.
- Public AI/SEO discovery now includes slug-based product detail lookup, canonical `/product/{slug}` URLs, Product/FAQ/Breadcrumb/Organization/LocalBusiness/SearchAction JSON-LD on product pages, `/robots.txt`, sitemap endpoints, and `/ai-catalog.json` for AI-readable catalog discovery. Storefront search also handles jewellery misspellings, Marathi/Hinglish category words, and budget-style searches.
- Product create/update queues async OpenAI SEO/AI description generation for website-visible products using the existing OpenAI configuration so admin save stays fast.
- WhatsApp bot product replies now send text first and image/action cards afterward so customers receive an immediate response even when media delivery is slower. Persisted bot traces include incoming message, intent, search text, budget range, conversation stage, matched products, AI response, provider message id, and failure reason to make intent/product matching easier to debug from admin.
- EC2 release `local-20260522143002` went live with the Sneat admin, WhatsApp bot trace/debug screens, public SEO discovery endpoints, and smart product search. Deployment required clearing disposable Docker layer/cache data because the root volume was full; the persistent Postgres data under `/mnt/retail-data/postgres` was not touched. Live checks for app routes, product search, sitemap endpoints, robots, ai-catalog, and product slug pages passed.
- EC2 release `local-20260522150704` is live with the admin panel cleanup/navigation refactor and campaign image generation fix. Billing, Inventory, CRM, Support, Campaign, Reports, Salesperson, Site Interaction, and Users navigation was simplified; UI settings were removed; old WhatsApp admin routes fall back to Support; and campaign image generation now uploads OpenAI image bytes directly like category icons. Live checks for health, storefront, login, admin app, CRM Customer Info, Support, product search, and `ai-catalog.json` passed.

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
