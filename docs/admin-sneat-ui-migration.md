# Admin Sneat UI Migration

Date: 2026-05-22

Status: Prepared locally. Production deployment pending final user approval.

## Objective

Migrate the Krishnai admin application to the Sneat-inspired admin shell while preserving existing authentication, permissions, pages, APIs, business rules, workflows, validations, alerts, and integrations.

## Scope

- Replaced the authenticated admin shell with the Sneat layout, side drawer, navbar, submenu navigation, material-style cards, and UI settings drawer.
- Preserved all existing route components for Billing, Inventory, Customer CRM, Support Inbox, Campaign Studio, Reports, Salesperson Sales, Site Interaction, Brand Configuration, and Users.
- Added parent/child side menu groups so internal module tabs are reachable from the drawer.
- Added query-tab bridges for modules with existing internal tabs:
  - Inventory: products, categories, collections, brands
  - Customer CRM: overview, timeline, orders, preferences, search activity, login history, support, AI insights, notes
  - Support Inbox: active, archived
  - Reports: dashboard, sales, payments
  - Brand Configuration: brand, theme, social, Meta catalog
- Added persisted UI settings for collapsed menu, fixed navbar, compact density, rounded cards, and menu animations.
- Added a dev-only Vite `/api` proxy to `https://kpskrishnai.com` so local review uses live API behavior without changing production runtime configuration.

## Preserved Behavior

- Public storefront routes remain unchanged.
- Login/logout/session expiry handling remains unchanged.
- Role and permission checks still use the existing authenticated session permissions.
- Existing page components continue to call the same frontend services and backend API contracts.
- No backend DTO, database schema, API path, or business logic was changed for this UI shell migration.

## Validation Completed

- Frontend production build: passed with `npm run build`.
- Sneat CSS/font/icon assets load from `frontend/public/sneat-assets`.
- Admin routes compile with the new shell and existing route guards.
- Internal submenu links compile and map into existing page tabs where available.
- Local `/api/auth/login` smoke test reaches the live backend through the dev proxy and returns the expected `401 Invalid credentials` for an invalid login.

Build note:

- Vite reports one large JavaScript chunk warning (`> 500 kB`). This is a performance optimization opportunity, not a build failure.

## Manual Regression Checklist Before Production Deployment

- Authentication: valid login, invalid login, logout, expired session redirect.
- Billing: product search, cart, quote, checkout, invoice generation, latest invoices.
- Inventory: product list, create/edit/delete, category option management, collections, brands, uploads, category icon generation.
- Customer CRM: customer load, search, filters, selection, overview, timeline, preferences, search activity, login history, support, notes.
- Support Inbox: active/archived tabs, conversation selection, replies, product sharing, resolve/archive flows, unread alert.
- Campaign Studio: campaign list, create campaign, offers, approval queue, scheduled publishing, analytics.
- Reports: dashboard, sales report filters, Razorpay diagnostics, low stock, website orders.
- Salesperson Sales: date/user filters, trend cards, detailed records.
- Site Interaction: traffic metrics, source data, Maharashtra map, recent visits.
- Brand Configuration: business details, theme/media, social links, Meta catalog/feed preview.
- Users: create/edit users, role assignment, permission visibility, active/inactive behavior.
- Responsive: desktop, tablet, mobile drawer, collapsed menu, scroll behavior.
- Browser QA: Chrome/Safari/Edge smoke, no console errors, no broken assets.

## Known Gaps

- Full authenticated workflow regression requires logging into the local migrated shell with production-equivalent credentials.
- Automated end-to-end coverage is not yet added for every admin workflow.
- Chunk splitting can be added later to reduce the admin bundle size.

## Rollout Plan

1. Validate the migrated admin shell locally against live APIs.
2. Run the frontend build and backend tests required for the deployment window.
3. Deploy during a low-traffic window.
4. Smoke test login, navigation, Billing, Inventory, Support, and Customer CRM immediately after deploy.
5. Roll back to the previous deployed frontend artifact if any critical workflow fails.

## Rollback Boundary

Rollback is frontend-only for this migration shell. Revert the Sneat shell files, Sneat public assets, and query-tab bridge changes while preserving backend changes and unrelated feature work.
