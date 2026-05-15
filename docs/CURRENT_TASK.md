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
- Improve WhatsApp quick menu, product/detail/cart/order text, repeated product behavior, and campaign source links.

Implementation:
- WhatsApp main menu uses the requested flow: View Collections, Offers, Track Order, Talk to Shop, Show More.
- Category rows are built from active DB category options and live product categories.
- Category labels prefer DB display names.
- Product search uses dynamic catalog categories for routing.
- Product cards are filtered against the selected/requested category before sending images or list rows.
- If upstream search returns only category mismatches, catalog fallback is used for matching products.
- Welcome sends quick buttons and a Show More list.
- Product names are checked for category conflicts so a Necklace-named item cannot appear in Bangles results just because stored category is wrong.
- Repeated same-category searches exclude products already shown in the session.
- Product details, cart, and order replies use clearer action/status text.
- Welcome menu is text-first without a logo image to keep message order clear.
- Product search replies send a concise intro, up to four matching product images, and then a Details/Add/More Similar action list.
- Order tracking uses a text progress bar and shows the being-delivered items.
- `/product/{productId}` campaign links are public SPA routes, record valid source visits, and render the linked product.
- Public product category filtering accepts category codes and display/category aliases.
- Campaign Studio create form shows validation/API errors near the Save buttons and scrolls them into view when a submission is blocked.
