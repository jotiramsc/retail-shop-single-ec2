# Sneat Admin Dashboard API Notes

## Implemented With Existing APIs

The new Sneat-style central dashboard currently uses existing live endpoints only:

- Daily sales report
- Product inventory list
- Customer list
- Support inbox summary
- Marketing campaign list
- User list, when the signed-in role has access

No backend contract change is required for the current dashboard cards, menu routing, CRM dashboard, campaign dashboard, inventory screen split, support inbox split, reports split, and brand configuration split.

## Optional APIs For Full Sneat CRM Parity

These are not blockers for the current migration, but they would make the dashboard richer and faster because the frontend would not need to derive analytics from multiple paged endpoints:

- `GET /api/dashboard/summary`
  - Returns sales, order count, product count, customer count, support open/unread count, campaign count, and low-stock count in one response.

- `GET /api/dashboard/sales-activity?range=7d|30d|90d`
  - Returns date-series revenue, orders, visitors, and conversion activity for charts.

- `GET /api/dashboard/top-products?metric=sales|stock|views&limit=10`
  - Returns top products with image, SKU, category, quantity, revenue, and low-stock status.

- `GET /api/dashboard/customer-activity?limit=20`
  - Returns recent customer activity events such as login, search, product view, order, chat, and offer click.

- `GET /api/dashboard/campaign-performance?range=30d`
  - Returns campaign delivery, approval, schedule, clicks, conversions, and revenue attribution.

- `GET /api/dashboard/team-performance?range=30d`
  - Returns salesperson or admin activity, invoices handled, support replies, and campaign approvals.

## Recommendation

Keep the current implementation for the UI migration because it preserves existing behavior. Add the optional dashboard aggregation APIs later for production performance and richer charts.
