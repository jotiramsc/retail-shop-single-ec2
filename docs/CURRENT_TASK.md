# CURRENT TASK

## Current Task: Admin Panel Cleanup, Navigation Refactor, and Campaign Image Generation Fix

Status: Completed locally and build-verified. Not deployed in this pass.

Scope:
- Admin sidebar was simplified so duplicate/dead menu entries are no longer shown.
- Billing now exposes only the main Billing workflow; old Checkout and Latest Invoices admin submenu links redirect back to Billing.
- Inventory now exposes Products and Categories only; old Collections and Brands admin routes redirect to Products.
- Customer CRM now keeps Dashboard, Customer Info, Search Activity, Login History, Support Chat, and AI Insights. Customer List redirects directly to Customer Info.
- WhatsApp admin/debug menus and frontend page were removed from the admin shell. Old `/app/whatsapp/*` links redirect to Support Active Conversations.
- Campaign Studio now keeps Campaign Dashboard, Campaign List, Create Campaign, Offers, and Approval Queue. Removed campaign template/audience/analytics/scheduler/report/automation menu entries redirect to active campaign screens.
- Reports now keeps Dashboard, Sales reports, and Razorpay diagnostics. Duplicate low-stock/website-order report routes redirect to Dashboard.
- Salesperson Sales, Site Interaction, and Users were flattened to remove duplicate submenu entries that opened the same screen.
- UI settings/options drawer and related frontend preference storage were removed.
- Sidebar behavior now keeps only one submenu expanded; navigating to a route collapses unrelated submenus automatically.
- Sneat-style transitions were tightened for sidebar collapse, submenu open/close, route entry, card hover, image hover, dropdowns, and modal surfaces.
- Campaign image generation now uses the same stable approach as category icon generation: OpenAI image bytes are uploaded directly to `marketing-campaigns` with the returned content type, avoiding the extra Java overlay/composition step that could fail or delay uploads.
- Campaign OpenAI image generation now has a bounded timeout matching the category icon generation behavior.

Verification:
- Frontend `npm run build` passed.
- Backend `./mvnw -DskipTests package` passed.
- Local Vite login route loaded without browser console errors.
- Full authenticated admin browser smoke was not completed because the in-app browser's read-only evaluation surface could not seed session storage; production build verification covers compile/runtime bundle integrity.
