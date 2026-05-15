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
    - Product search replies send a concise intro, up to five matching images, and then only quick buttons: Details, Add to Cart, Yes show more.
    - Product search no longer sends the separate collapsed View Products list card that could appear before/after images in the wrong order.
    - Product image captions include VIEW/ADD reply codes for each shown item.
    - More product pagination shows the next five items and stops with "No more products available in this category."
    - Order tracking uses a text progress bar and lists being-delivered items in the status card.
    - Product-name/category conflict guard rejects bad product data such as Necklace-named items in Bangles results.
    - `/product/{productId}` source-tracked links are public SPA routes, record campaign lead visits, and show the product page.
    - Public product listing category filters accept DB codes and display/category aliases.
    - Campaign Studio create form shows blocked-submit validation/API errors near Save buttons and scrolls them into view.
- Single-Agent WhatsApp Support Inbox
  - Changed files:
    - `backend/src/main/java/com/retailshop/controller/SupportInboxController.java`
    - `backend/src/main/java/com/retailshop/service/SupportInboxService.java`
    - `backend/src/main/java/com/retailshop/service/impl/SupportInboxServiceImpl.java`
    - `backend/src/main/java/com/retailshop/dto/Support*.java`
    - `frontend/src/pages/SupportInboxPage.jsx`
    - `frontend/src/App.jsx`
    - `frontend/src/services/retailService.js`
    - `frontend/src/styles/global.css`
  - Implementation notes:
    - Uses existing `omnichannel_conversations` and `omnichannel_conversation_messages`.
    - Admin Support nav shows unread badge via polling.
    - Support page lists/searches WhatsApp chats, shows history, sends replies, sends inventory products, and marks chats resolved.
    - WebSocket is deferred; polling is the first production-safe implementation for the one-agent model.
- TASK-17: Support Page Product Suggestion and WhatsApp Forwarding
  - Changed files:
    - `backend/src/main/java/com/retailshop/dto/SupportConversationMessageResponse.java`
    - `backend/src/main/java/com/retailshop/service/impl/SupportInboxServiceImpl.java`
    - `frontend/src/pages/SupportInboxPage.jsx`
    - `frontend/src/styles/global.css`
    - `docs/CURRENT_TASK.md`
    - `docs/TASK_TRACKER.md`
    - `docs/krishnai.md`
  - Implementation notes:
    - Support product picker searches existing inventory by product name, category, SKU/item code, and simple price ranges.
    - Product cards show image, name, category, SKU, price/offer price, stock status, and quantity.
    - Admin sends with "Send to WhatsApp"; out-of-stock products require confirmation before sending.
    - WhatsApp suggestion includes support-team note, product name, price/offer price, stock, product link, and image when public image URL exists.
    - Product send history stores productId, product name, sentBy, timestamp, customer mobile, provider message id, and WhatsApp status metadata.
  - Testing:
    - `backend ./mvnw test` passed: 62 tests.
    - `frontend npm run build` passed.

## Pending

None
