TASK: Implement Dynamic Facebook Catalog Sync in Brand Configuration

Read first:
- krishnai.md
- TASK_TRACKER.md
- CURRENT_TASK.md

Rules:
- Do not depend on chat context.
- Work only on this task.
- First show locally and verify.
- Then deploy to EC2.
- Do not wait for manual deployment approval.
- Keep UI simple, clean, and compact.
- Use existing product/category/brand configuration tables where possible.
- Update krishnai.md, TASK_TRACKER.md, and CURRENT_TASK.md after completion.

==================================================
GOAL
==================================================

Add dynamic Facebook Catalog support for kpskrishnai.com.

Admin should be able to configure Facebook catalog from:

Admin → Brand Configuration → Facebook Catalog tab

Website should generate secure dynamic feed URLs:

https://kpskrishnai.com/api/meta/catalog-feed.xml?token={token}
https://kpskrishnai.com/api/meta/catalog-feed.csv?token={token}

Facebook/Meta Commerce Manager will fetch products automatically using scheduled feed.

No direct Facebook API integration required.

==================================================
1. BRAND CONFIGURATION UI
==================================================

Modify existing Brand Configuration screen.

Add new tab:

Facebook Catalog

Tabs should look like:

- Brand Details
- Theme / Logo
- Social Links
- Facebook Catalog

Inside Facebook Catalog tab add sections:

--------------------------------------------------
A. Catalog Sync Settings
--------------------------------------------------

Fields:

[ ] Enable Facebook Catalog Sync

Meta Pixel ID
Input placeholder:
123456789012345

Feed Security Token
Input placeholder:
kps_meta_2026_secure

Buttons:
- Generate Token
- Save Configuration

Behavior:
- Generate Token creates a random secure token.
- Token should be saved in backend.
- Token should be used in feed URLs.
- Do not expose admin password or login details.

--------------------------------------------------
B. Feed URLs
--------------------------------------------------

Show readonly fields with copy buttons:

XML Feed URL:
https://kpskrishnai.com/api/meta/catalog-feed.xml?token={saved_token}

CSV Feed URL:
https://kpskrishnai.com/api/meta/catalog-feed.csv?token={saved_token}

Buttons:
- Copy XML URL
- Copy CSV URL
- Open XML Feed
- Open CSV Feed

--------------------------------------------------
C. Feed Status
--------------------------------------------------

Show compact status cards:

- Facebook Sync: Enabled / Disabled
- Pixel: Configured / Not Configured
- Synced Categories: count
- Synced Products: count
- Last Feed Generated: date time

==================================================
2. DATABASE CHANGES
==================================================

Use existing brand configuration table if available.

Add fields:

facebook_catalog_enabled BOOLEAN DEFAULT false
meta_pixel_id VARCHAR(100)
facebook_feed_token VARCHAR(255)
facebook_feed_last_generated_at DATETIME

Add fields in category table:

facebook_sync_enabled BOOLEAN DEFAULT false
facebook_category VARCHAR(500)
facebook_collection_name VARCHAR(255)

Add field in product table:

facebook_sync_enabled BOOLEAN DEFAULT false

Create migration script using existing migration pattern.

==================================================
3. CATEGORY ADMIN UI
==================================================

On category create/edit screen add compact section:

Facebook Catalog Settings

Fields:

[ ] Sync this category to Facebook

Facebook Product Category
Placeholder:
Apparel & Accessories > Jewelry > Necklaces

Facebook Collection Name
Placeholder:
Necklace Collection

Example category mappings:
- Necklace → Apparel & Accessories > Jewelry > Necklaces
- Earrings → Apparel & Accessories > Jewelry > Earrings
- Bangles → Apparel & Accessories > Jewelry > Bracelets
- Cosmetics → Health & Beauty > Cosmetics

Behavior:
- If category sync is OFF, products under this category must not appear in feed.
- Keep fields hidden/collapsed if sync OFF.

==================================================
4. PRODUCT ADMIN UI
==================================================

On product create/edit screen add compact section:

Facebook Catalog

Fields:

[ ] Sync this product to Facebook Catalog

Readonly display:
Facebook Category:
Auto picked from selected category.

Validation when sync enabled:
- Product must have name.
- Product must have price.
- Product must have image.
- Product must be visible/active.
- Product must belong to Facebook synced category.

Do not block saving product unless user enabled Facebook sync and required feed fields are missing.

==================================================
5. FEED SECURITY RULE
==================================================

Create public endpoints:

GET /api/meta/catalog-feed.xml
GET /api/meta/catalog-feed.csv

Security logic:

- If Facebook Catalog Sync is OFF:
  return 404 or clear disabled message.

- If token is configured:
  token query parameter is mandatory.

Example:
?token=kps_meta_2026_secure

- If token missing or invalid:
  return HTTP 403.

- If token valid:
  return feed.

Important:
- Do not require admin login.
- Do not expose customer/admin/order data.
- Feed must be read-only.
- Only product catalog data should be returned.

==================================================
6. XML FEED FORMAT
==================================================

Generate RSS XML feed compatible with Meta Commerce Manager.

Use format:

<rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">
  <channel>
    <title>KRISHNAI Pearl Shopee Catalog</title>
    <link>https://kpskrishnai.com</link>
    <description>Dynamic product catalog feed</description>

    <item>
      <g:id>KPS-NCK-001</g:id>
      <g:title>Ruby Necklace</g:title>
      <g:description>Premium necklace</g:description>
      <g:availability>in stock</g:availability>
      <g:condition>new</g:condition>
      <g:price>9999.00 INR</g:price>
      <g:sale_price>7999.20 INR</g:sale_price>
      <g:link>https://kpskrishnai.com/product/ruby-necklace</g:link>
      <g:image_link>https://kpskrishnai.com/images/products/1.jpg</g:image_link>
      <g:brand>KRISHNAI Pearl Shopee</g:brand>
      <g:google_product_category>Apparel & Accessories > Jewelry > Necklaces</g:google_product_category>
      <g:product_type>Necklace</g:product_type>
    </item>

  </channel>
</rss>

Required XML escaping:
- Escape &, <, >, ", '
- Do not generate invalid XML.

==================================================
7. CSV FEED FORMAT
==================================================

Generate CSV with headers:

id,title,description,availability,condition,price,sale_price,link,image_link,brand,google_product_category,product_type

Example:

KPS-NCK-001,Ruby Necklace,Premium necklace,in stock,new,9999.00 INR,7999.20 INR,https://kpskrishnai.com/product/ruby-necklace,https://kpskrishnai.com/images/products/1.jpg,KRISHNAI Pearl Shopee,Apparel & Accessories > Jewelry > Necklaces,Necklace

CSV requirements:
- Escape commas and quotes properly.
- Use UTF-8.
- Set content type:
  text/csv

==================================================
8. PRODUCTS INCLUDED IN FEED
==================================================

Feed must include only products where:

- Facebook catalog enabled at brand level
- Product is active/visible
- Product facebook_sync_enabled = true
- Product category facebook_sync_enabled = true
- Product has valid image
- Product has valid public product URL
- Product has valid price

Exclude:
- Hidden products
- Deleted products
- Draft products
- Out of stock products if stock tracking says unavailable
- Products without image
- Categories with sync OFF

==================================================
9. PRICE LOGIC
==================================================

If active offer/discount applies:

g:price = original price
g:sale_price = final discounted price

If no offer:

g:price = product price
Do not include sale_price or keep empty in CSV.

Currency format:

9999.00 INR

Use existing website offer logic where possible.

==================================================
10. IMAGE LOGIC
==================================================

Use product image in this priority:

1. Main product image
2. First active gallery image

Image URL must be full public HTTPS URL.

Example:

https://kpskrishnai.com/uploads/products/necklace1.jpg

Do not return:
- relative image path
- localhost URL
- private S3 URL
- broken image URL

==================================================
11. PRODUCT URL LOGIC
==================================================

Each product must have full public URL.

Example:

https://kpskrishnai.com/product/ruby-necklace

Use existing slug if available.

Fallback:
https://kpskrishnai.com/product/{productId}

==================================================
12. META PIXEL
==================================================

In Facebook Catalog tab, allow saving Meta Pixel ID.

If Pixel ID exists:
Inject Meta Pixel script on public website pages.

Track:

PageView:
all pages

ViewContent:
product detail page

AddToCart:
when customer adds product to cart

Purchase:
order success page

ViewContent payload:

content_ids: [productSkuOrId]
content_type: "product"
value: product final price
currency: "INR"

AddToCart payload:

content_ids: [productSkuOrId]
content_type: "product"
value: product final price
currency: "INR"

Purchase payload:

content_ids: purchased product ids
content_type: "product"
value: order total
currency: "INR"

==================================================
13. PRODUCT MICRODATA
==================================================

On public product detail page add schema.org Product microdata or JSON-LD.

Preferred: JSON-LD.

Include:

- @context
- @type Product
- name
- image
- description
- sku
- brand
- offers
- priceCurrency INR
- price
- availability
- url

Example:

<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "Product",
  "name": "Ruby Necklace",
  "image": ["https://kpskrishnai.com/uploads/products/necklace1.jpg"],
  "description": "Premium necklace",
  "sku": "KPS-NCK-001",
  "brand": {
    "@type": "Brand",
    "name": "KRISHNAI Pearl Shopee"
  },
  "offers": {
    "@type": "Offer",
    "url": "https://kpskrishnai.com/product/ruby-necklace",
    "priceCurrency": "INR",
    "price": "7999.20",
    "availability": "https://schema.org/InStock"
  }
}
</script>

==================================================
14. FEED CACHE AND PERFORMANCE
==================================================

Feed should support 5000+ products.

Requirements:
- Avoid heavy image processing.
- Do not load image files in memory.
- Query only required fields.
- Cache feed for 5 minutes.
- Update facebook_feed_last_generated_at when feed is generated.
- Return fast response.

==================================================
15. ADMIN FEED PREVIEW
==================================================

In Brand Configuration → Facebook Catalog tab add:

Preview Feed

Show small table:

Columns:
- Product ID
- Product Name
- Category
- Price
- Sale Price
- Status
- Issue

Show first 20 products.

Status examples:
- Ready
- Missing Image
- Category Sync Off
- Product Sync Off
- Hidden Product
- Missing Price

This helps admin fix feed issues before adding URL to Facebook.

==================================================
16. VALIDATIONS
==================================================

Show warnings but do not break entire admin page.

Examples:

- Meta Pixel ID is empty
- Feed token not generated
- 3 products skipped due to missing image
- 2 categories not mapped to Facebook category
- Feed disabled

==================================================
17. BACKEND API SUMMARY
==================================================

Create/update APIs as needed:

Brand config:
GET /api/admin/brand-config
PUT /api/admin/brand-config

Token:
POST /api/admin/brand-config/facebook-feed-token/generate

Feed preview:
GET /api/admin/brand-config/facebook-feed-preview

Public feed:
GET /api/meta/catalog-feed.xml?token={token}
GET /api/meta/catalog-feed.csv?token={token}

==================================================
18. LOCAL TESTING CHECKLIST
==================================================

Verify locally:

1. Facebook Catalog tab appears in Brand Configuration.
2. Enable sync can be saved.
3. Pixel ID can be saved.
4. Token can be generated.
5. XML and CSV URLs show correctly.
6. Copy buttons work.
7. Open XML feed works.
8. Open CSV feed works.
9. Invalid token returns 403.
10. Missing token returns 403 if token configured.
11. Sync OFF disables feed.
12. Category sync ON/OFF works.
13. Product sync ON/OFF works.
14. Hidden products excluded.
15. Products without image excluded.
16. Offer price appears as sale_price.
17. Product image URLs are full HTTPS.
18. Product links open correctly.
19. Pixel script appears on public website.
20. ViewContent event fires on product page.
21. AddToCart event fires on add to cart.
22. Purchase event fires on order success page.
23. Product JSON-LD appears on product detail page.
24. XML validates without errors.
25. CSV opens correctly.

==================================================
19. DEPLOYMENT
==================================================

After local verification:
- Deploy backend
- Deploy frontend
- Run database migration
- Verify public feed URL:

https://kpskrishnai.com/api/meta/catalog-feed.xml?token={token}

- Verify no admin login is required for feed URL.
- Verify invalid token gives 403.
- Verify feed works in browser.

==================================================
20. FACEBOOK CONFIGURATION GUIDE TO ADD IN DOCS
==================================================

Add this guide in krishnai.md:

Facebook side steps:

1. Go to Meta Commerce Manager.
2. Create catalog.
3. Choose Online products.
4. Keep partner platform OFF.
5. Choose business portfolio.
6. Catalog name:
   KPS_JEWELRY_CATALOG

7. Add products:
   Use a URL or Google Sheets

8. Platform:
   Commerce Manager

9. Feed URL:
   https://kpskrishnai.com/api/meta/catalog-feed.xml?token={token}

10. Currency:
    INR

11. Schedule:
    Daily or hourly

12. After save, check:
    Catalog → Data Sources → Diagnostics

13. Connect Pixel:
    Catalog → Settings → Event Sources → Connect Pixel

==================================================
21. FINAL DOCUMENT UPDATE
==================================================

Update:
- krishnai.md
- TASK_TRACKER.md
- CURRENT_TASK.md

Mention:
- Facebook Catalog tab added in Brand Configuration
- Category-level sync added
- Product-level sync added
- Secure XML/CSV feed URLs added
- Meta Pixel configuration added
- Product JSON-LD added
- Admin feed preview added
- Facebook setup guide added