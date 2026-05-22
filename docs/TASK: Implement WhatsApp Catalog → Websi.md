TASK: Implement Meta/Facebook Catalog + Website Product Flow with Multiple Images

Read first:
- krishnai.md
- TASK_TRACKER.md
- CURRENT_TASK.md

Rules:
- Work only on this task.
- Do not depend on chat context.
- Verify locally first.
- Deploy to EC2 after verification.
- Update krishnai.md and TASK_TRACKER.md after completion.
- Keep existing UI and functionality stable.
- Mobile-first implementation.

==================================================
OBJECTIVE
==================================================

Implement complete product flow for:

Meta/Facebook Catalog
        +
WhatsApp Catalog
        +
KPS Website Product Pages
        +
Website Cart

Main requirement:
- Products should support multiple images.
- Same product data should be usable for website and Meta catalog.
- WhatsApp/Facebook catalog products should open website product/cart links.
- Customer should browse catalog → open website → add to cart → checkout.

==================================================
1. PRODUCT MULTIPLE IMAGES
==================================================

Add multiple image support for products.

Requirements:
- Product can have 1 primary image.
- Product can have multiple gallery images.
- Admin can upload multiple images.
- Admin can reorder images.
- Admin can mark primary image.
- Admin can delete image.
- Customer product page should show image slider/gallery.
- Product listing should show primary image.
- WhatsApp/catalog should use primary image first.

Suggested table:

product_images
- id
- product_id
- image_url
- image_type: PRIMARY / GALLERY
- sort_order
- alt_text
- is_active
- created_at
- updated_at

==================================================
2. PRODUCT PAGE IMAGE GALLERY
==================================================

On customer product view page:

Add:
- main image preview
- thumbnail gallery
- swipe support on mobile
- next/previous arrows
- zoom on click
- lazy loading
- fallback image if missing

Design:
- premium jewelry look
- clean white/gold theme
- soft shadows
- mobile optimized

==================================================
3. ADMIN PRODUCT IMAGE UX
==================================================

On admin product form:

Add section:
Product Images

Features:
- drag and drop upload
- multiple image selection
- preview before save
- mark primary image
- reorder images
- delete image
- alt text field
- validation for image size/type

Allowed:
- jpg
- jpeg
- png
- webp

==================================================
4. META CATALOG FIELDS
==================================================

Add catalog mapping fields to product:

products table:
- meta_catalog_id
- meta_retailer_id
- meta_sync_status
- meta_last_synced_at
- meta_sync_error
- catalog_enabled

Generate meta_retailer_id automatically using product/category code.

Example:
- NK001
- ER001
- BG001
- COS001

Keep it unique.

==================================================
5. WEBSITE DEEP LINKS
==================================================

Create product links:

https://kpskrishnai.com/product/{slug}

Add-to-cart link:

https://kpskrishnai.com/cart/add?product={meta_retailer_id}&source=whatsapp

Campaign link:

https://kpskrishnai.com/product/{slug}?source=facebook&campaign={campaignName}

Requirements:
- product lookup by slug
- product lookup by product id
- product lookup by meta_retailer_id
- invalid product redirects to product listing

==================================================
6. CART ADD LINK
==================================================

Create route:

GET /cart/add

Query params:
- product
- qty
- source
- campaign

Behavior:
- add product to cart
- default qty = 1
- create guest cart if user not logged in
- redirect to cart page
- preserve source/campaign tracking

==================================================
7. META CATALOG EXPORT API
==================================================

Create API to export products for Meta catalog.

Endpoint:

GET /api/catalog/meta-feed

Return catalog-compatible feed with:

- id / retailer_id
- title
- description
- availability
- condition
- price
- sale_price
- link
- image_link
- additional_image_link
- brand
- google_product_category / product_type
- inventory
- custom_label_0 category
- custom_label_1 offer
- custom_label_2 source

Important:
- image_link = primary image
- additional_image_link = gallery images
- link = website product page
- sale_price = offer price if active

==================================================
8. META CSV/XML FEED SUPPORT
==================================================

Support downloadable feed:

GET /api/catalog/meta-feed.csv
GET /api/catalog/meta-feed.xml

Use same product logic.

Only export:
- active products
- visible products
- catalog_enabled = true
- products with at least one image
- products with price

==================================================
9. CATALOG SYNC ADMIN PAGE
==================================================

Create admin page:

Catalog Sync

Show:
- total products
- catalog enabled products
- products missing image
- products missing price
- last sync time
- sync errors

Actions:
- enable/disable catalog per product
- copy feed URL
- copy product deep link
- copy add-to-cart link
- generate QR code
- mark product as synced manually

==================================================
10. WHATSAPP MESSAGE LINK SUPPORT
==================================================

Generate WhatsApp-ready product message:

Example:

✨ KRISHNAI Pearl Shopee

{productName}

₹{offerPrice}
{discount}% OFF
MRP ₹{originalPrice}

View Product:
{productUrl}

Add to Cart:
{cartAddUrl}

Requirements:
- copy message button
- include primary product image
- include product deep link
- include add-to-cart link
- include campaign tracking params

==================================================
11. PRODUCT LISTING PRICE DISPLAY
==================================================

On customer listing and product page show:

₹9,999.00   crossed
20% OFF
₹7,999.20

Use best active offer if available.

Apply same logic in:
- listing page
- product detail page
- catalog feed
- WhatsApp message generation

==================================================
12. ANALYTICS TRACKING
==================================================

Track:
- source
- campaign
- product views
- add-to-cart clicks
- checkout started
- order completed

Create table:

marketing_click_events
- id
- session_id
- customer_id
- product_id
- source
- campaign
- event_type
- ip
- user_agent
- created_at

Event types:
- PRODUCT_VIEW
- ADD_TO_CART
- CHECKOUT_STARTED
- ORDER_CREATED

==================================================
13. ORDER SOURCE TRACKING
==================================================

When order is created, save:
- source
- campaign
- medium
- product_source

Example:
source = whatsapp
campaign = bridal_offer

Show in admin order detail:
- Order Source
- Campaign
- First product clicked
- Last product viewed

==================================================
14. IMAGE STORAGE
==================================================

Use existing image storage if available.

If S3 is configured:
- upload images to S3
- store public URL
- optimize image size
- generate thumbnail if possible

If local storage is currently used:
- keep compatible
- do not break existing images

==================================================
15. VALIDATIONS
==================================================

Admin validation:
- product must have primary image before catalog enable
- product must have price
- product must have name
- product must have category
- meta_retailer_id must be unique

Feed validation:
- skip invalid products
- show skipped reason in admin

==================================================
16. TEST SCENARIOS
==================================================

Test locally:

1. Add product with multiple images
2. Mark primary image
3. Product listing shows primary image
4. Product detail shows gallery
5. Product URL opens correctly
6. Cart add link adds item
7. Guest cart works
8. Logged-in cart works
9. Meta feed contains primary image
10. Meta feed contains additional images
11. Offer price appears correctly
12. WhatsApp link opens correct product
13. Analytics stores source/campaign
14. Order stores source/campaign
15. Invalid product redirects safely

==================================================
17. DEPLOYMENT
==================================================

After local verification:
- run DB migration
- deploy backend
- deploy frontend
- verify HTTPS feed URL
- verify product images are public
- verify catalog feed opens without login
- verify mobile product page
- verify cart add link from phone

==================================================
18. DOCUMENTATION
==================================================

Update:
- krishnai.md
- TASK_TRACKER.md
- CURRENT_TASK.md

Document:
- Meta catalog feed URL
- product image structure
- catalog sync rules
- WhatsApp deep link format
- analytics tracking flow
- admin usage steps

==================================================
EXPECTED FLOW
==================================================

Admin adds product with multiple images
        ↓
Primary image selected
        ↓
Product appears on website
        ↓
Product appears in Meta catalog feed
        ↓
Facebook/WhatsApp catalog shows product
        ↓
Customer opens product from catalog
        ↓
Website product page opens
        ↓
Customer adds to cart
        ↓
Checkout completed
        ↓
Order source saved as Facebook/WhatsApp