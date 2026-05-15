# Task Tracker

## In Progress

None

## Completed

- TASK-1: Campaign Lead Tracking Backend
- TASK-2: Campaign Studio source-wise analytics only.
- TASK-3: Fix WhatsApp bot dynamic category issue.
- TASK-4: WhatsApp interactive menu UI.
- TASK-5: Visual category home.
- TASK-6: AI intent-based search.
- TASK-7: Product listing cards.
- TASK-8: Product detail screen.
- TASK-9: Cart and order tracking UI.
- WhatsApp Bot Dynamic Category/Menu/Product Flow
  - Changed files:
    - `backend/src/main/java/com/retailshop/service/impl/WhatsAppSalesBotServiceImpl.java`
    - `backend/src/test/java/com/retailshop/service/impl/WhatsAppSalesBotServiceImplTest.java`
    - `docs/CURRENT_TASK.md`
    - `docs/TASK_TRACKER.md`
    - `docs/krishnai.md`
  - Implementation notes:
    - Categories load from active `product_categories` first.
    - Live product categories are used as a fallback only.
    - Static fallback category menu rows were removed.
    - Main WhatsApp menu matches View Collections / Offers / Track Order / Talk to Shop / Show More.
    - Regression test covers dynamic `BGL -> Bangles` category rendering.
    - Regression test covers Bangles selection rejecting a returned Necklace card and falling back to matching Bangles products.
    - Welcome uses quick buttons plus a Show More list.
    - Same-category product replies exclude already shown products to avoid repeating the same card.
    - Welcome menu is text-first without a logo image to keep WhatsApp message order clean.
    - Product search replies send a concise intro, up to four matching images, and then a Details/Add/More Similar action list.
    - Order tracking uses a text progress bar and lists being-delivered items in the status card.
    - Product-name/category conflict guard rejects bad product data such as Necklace-named items in Bangles results.
    - `/product/{productId}` source-tracked links are public SPA routes, record campaign lead visits, and show the product page.
    - Public product listing category filters accept DB codes and display/category aliases.
    - Campaign Studio create form shows blocked-submit validation/API errors near Save buttons and scrolls them into view.

## Pending

None
