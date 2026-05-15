# CURRENT TASK

## WhatsApp Bot Dynamic Category/Menu/Product Flow

Status: Completed

Scope:
- Fix WhatsApp bot category/menu/product flow so categories are loaded dynamically.
- Use configured `product_categories` first.
- Fall back to live product categories only when configured categories are absent.
- Remove static fallback category menu rows.
- Keep product search/routing dynamic through catalog category values.
- Fix selected category product leakage, e.g. Bangles must not render Necklace product cards.

Implementation:
- WhatsApp main menu uses the requested flow: View Collections, Offers, Track Order, Talk to Shop, Show More.
- Category rows are built from active DB category options and live product categories.
- Category labels prefer DB display names.
- Product search uses dynamic catalog categories for routing.
- Product cards are filtered against the selected/requested category before sending images or list rows.
- If upstream search returns only category mismatches, catalog fallback is used for matching products.
