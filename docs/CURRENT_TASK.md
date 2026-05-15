# CURRENT TASK

## TASK-C: Inventory Visibility Checkboxes

Status: Completed

Scope:
- Add product visibility controls for public website and local shop billing.
- Default both options to enabled for new and existing products.
- Keep hidden website products out of public storefront APIs.
- Keep local-shop-only disabled products out of billing product pickers.

Implementation:
- Products now store `showOnWebsite` and `useForBilling`, mapped to `show_on_website` and `use_for_billing`.
- Database defaults both flags to true, preserving existing inventory visibility.
- Inventory form shows "Show on Website" and "Use for Shop Billing" checkboxes, both selected for new products.
- Public catalog, homepage, trending, and direct product lookup only return products with `showOnWebsite=true`.
- Billing loads only products with `useForBilling=true`.
