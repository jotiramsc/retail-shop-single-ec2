# Omnichannel Commerce Configuration

This document covers the AI-powered social-to-website commerce integration for the existing React storefront, Spring Boot backend, PostgreSQL database, and EC2 deployment.

## What Is Integrated

- Lead capture endpoint for Instagram, Facebook, WhatsApp, n8n, or any commerce bot.
- Product search endpoint that returns website-ready product cards with image, price, stock label, product URL, cart URL, and checkout URL.
- Campaign deep links that can auto-add a product to the website cart and store a campaign coupon.
- Meta webhook verification and inbound webhook storage.
- PostgreSQL tables for omnichannel leads, conversations, webhook events, and recommendation logs.
- Existing marketing approval flow remains separate and approval-gated before publishing.

## Website Link Format

Use these URLs in WhatsApp, Instagram DM, Facebook Messenger, and n8n generated content:

```text
https://kpskrishnai.com/products?autoAdd=<PRODUCT_UUID>&redirect=cart&coupon=WELCOME10&utm_source=whatsapp&utm_medium=ai_commerce&utm_campaign=<CAMPAIGN_NAME>
https://kpskrishnai.com/products?autoAdd=<PRODUCT_UUID>&redirect=checkout&coupon=WELCOME10&utm_source=instagram&utm_medium=ai_commerce&utm_campaign=<CAMPAIGN_NAME>
```

Behavior:
- `autoAdd` adds the product to cart.
- `redirect=cart` sends the customer to cart after adding.
- `redirect=checkout` sends logged-in customers to checkout and guests to OTP login with checkout redirect.
- `coupon` is stored and applied by the cart/checkout offer flow when valid.

## Required Backend Environment

Core application:

```bash
DB_URL=jdbc:postgresql://<postgres-host>:5432/retail_shop
DB_USERNAME=<postgres-user>
DB_PASSWORD=<postgres-password>
DB_INIT_MODE=always
CORS_ALLOWED_ORIGINS=https://kpskrishnai.com,https://www.kpskrishnai.com
CUSTOMER_JWT_SECRET=<long-random-secret>
```

Omnichannel commerce:

```bash
OMNICHANNEL_WEBSITE_BASE_URL=https://kpskrishnai.com
OMNICHANNEL_WEBHOOK_VERIFY_TOKEN=<random-meta-verify-token>
OMNICHANNEL_WEBHOOK_SECRET=<meta-app-secret>
OMNICHANNEL_DEFAULT_COUPON_CODE=WELCOME10
OMNICHANNEL_MAX_PRODUCT_CARDS=6
```

OpenAI text and image generation:

```bash
MARKETING_AI_ENABLED=true
MARKETING_OPENAI_API_KEY=<openai-api-key>
MARKETING_OPENAI_MODEL=gpt-4.1-mini
MARKETING_OPENAI_IMAGE_MODEL=gpt-image-1.5
MARKETING_OPENAI_IMAGE_SIZE=1024x1024
MARKETING_OPENAI_IMAGE_QUALITY=medium
```

Image storage for generated creative assets:

The AI marketing generator reuses the same image storage already configured for product and branding images. Generated OpenAI image bytes are uploaded through the existing `ImageUploadService` into the current S3 bucket, under the `marketing-campaigns` category, and the stored `imageUrl` becomes the CloudFront URL.

```bash
AWS_REGION=ap-south-1
AWS_S3_BUCKET=<existing-private-bucket>
AWS_CLOUDFRONT_DOMAIN=<existing-cloudfront-domain>
```

On EC2, prefer the instance IAM role for S3 access. Do not add new AWS access keys unless running locally or outside AWS.

Meta publishing and webhook setup:

```bash
META_ACCESS_TOKEN=<meta-whatsapp-or-instagram-token>
FB_PAGE_ID=<facebook-page-id>
FB_PAGE_ACCESS_TOKEN=<facebook-page-access-token>
IG_BUSINESS_ACCOUNT_ID=<instagram-business-account-id>
META_GRAPH_VERSION=v23.0
MARKETING_META_ACCESS_TOKEN=${META_ACCESS_TOKEN}
MARKETING_FACEBOOK_PAGE_ID=${FB_PAGE_ID}
MARKETING_FACEBOOK_PAGE_ACCESS_TOKEN=${FB_PAGE_ACCESS_TOKEN}
MARKETING_INSTAGRAM_BUSINESS_ACCOUNT_ID=${IG_BUSINESS_ACCOUNT_ID}
MARKETING_META_GRAPH_VERSION=${META_GRAPH_VERSION}
```

Meta WhatsApp Cloud API for OTP, marketing send, and bot replies:

```bash
META_ACCESS_TOKEN=<permanent-system-user-token>
META_WHATSAPP_PHONE_NUMBER_ID=<cloud-api-phone-number-id>
META_WHATSAPP_OTP_TEMPLATE_NAME=<approved-authentication-template-name>
META_WHATSAPP_OTP_TEMPLATE_LANGUAGE=en_US
MARKETING_WHATSAPP_PHONE_NUMBER_ID=${META_WHATSAPP_PHONE_NUMBER_ID}
```

Payment and checkout, if live checkout is enabled:

```bash
RAZORPAY_KEY_ID=<razorpay-key-id>
RAZORPAY_KEY_SECRET=<razorpay-key-secret>
RAZORPAY_WEBHOOK_SECRET=<razorpay-webhook-secret>
```

Optional Redis for OTP/cart cache:

```bash
APP_REDIS_ENABLED=true
SPRING_DATA_REDIS_HOST=<redis-host>
SPRING_DATA_REDIS_PORT=6379
```

## External Platform Setup

Meta:
- Create or use a Meta app connected to the Facebook page and Instagram business account.
- Add webhook callback URL: `https://kpskrishnai.com/api/omnichannel/webhooks/meta`
- Use the same value as `OMNICHANNEL_WEBHOOK_VERIFY_TOKEN`.
- Subscribe to page messaging, Instagram messaging, comments, and lead/product inquiry events as needed.
- Set `OMNICHANNEL_WEBHOOK_SECRET` to the Meta app secret so `X-Hub-Signature-256` can be validated.

WhatsApp:
- Configure approved WhatsApp templates in Meta Business Manager.
- Subscribe the Meta app webhook to WhatsApp `messages`.
- Use `https://kpskrishnai.com/api/whatsapp/webhook` for conversational bot callbacks.
- For n8n-managed conversational approval, configure n8n’s WhatsApp inbound webhook separately.
- For app-managed campaign publishing, keep `META_ACCESS_TOKEN` and `MARKETING_WHATSAPP_PHONE_NUMBER_ID` configured.

n8n:
- Store credentials in n8n for OpenAI, WhatsApp provider, Meta, and the website backend.
- Backend base URL for HTTP nodes: `https://kpskrishnai.com/api`
- Product search: `GET /omnichannel/products/search`
- Lead capture: `POST /omnichannel/leads`
- Website product/cart links should come from the `buyNowUrl` or `checkoutUrl` returned by product search.

## n8n Workflow Prompt

Use this prompt to generate an n8n workflow template:

```text
Create an n8n workflow for Krishnai Pearl Shoppee social-commerce automation.

Goal:
Convert Instagram, Facebook, and WhatsApp inquiries into website purchases on https://kpskrishnai.com.

Stack and APIs:
- Website backend base URL: https://kpskrishnai.com/api
- Product search API: GET /omnichannel/products/search?q={{query}}&category={{category}}&occasion={{occasion}}&minPrice={{minPrice}}&maxPrice={{maxPrice}}&source={{channel}}&campaign={{campaignName}}&coupon={{couponCode}}
- Lead capture API: POST /omnichannel/leads
- AI text: OpenAI
- AI image: OpenAI image generation
- Approval: WhatsApp approval before publishing/sending
- Storage: n8n Data Table for workflow state, approvals, campaign history, and generated asset URLs

Workflow:
1. Trigger from WhatsApp inbound message, Instagram DM/comment event, Facebook Messenger event, or manual campaign form.
2. Normalize input fields: channel, customer name, mobile/external user id, query, budget, occasion, language, campaign name.
3. Call POST https://kpskrishnai.com/api/omnichannel/leads to store the lead and latest customer message.
4. Use OpenAI to classify intent into product search, price inquiry, offer inquiry, order support, or human handoff.
5. For product search, call GET https://kpskrishnai.com/api/omnichannel/products/search and request up to 6 products.
6. Generate a short Hinglish/English WhatsApp reply with product cards using returned name, price, shortBenefit, imageUrl, buyNowUrl, and checkoutUrl.
7. For campaign creation, generate Instagram caption, Facebook post, WhatsApp offer copy, and image prompt.
8. Generate OpenAI image asset only for campaign creation, not every customer reply.
9. Send approval message to owner WhatsApp with preview text, image URL, target platforms, coupon, and buttons: Approve, Edit, Reject.
10. If approved, send WhatsApp content to selected customers and publish/schedule Instagram/Facebook content using configured Meta nodes.
11. If rejected, store rejection reason and do not publish.
12. Log every lead, approval decision, sent message, post id, click URL, and failure in n8n Data Table.

Rules:
- Never publish or broadcast without WhatsApp approval.
- Always use backend returned buyNowUrl or checkoutUrl for purchase buttons.
- Use coupon WELCOME10 only when campaign config says coupon is active.
- Keep replies short, polite, premium, and conversion-focused.
- Support English, Hindi, and Hinglish.
- If confidence is low or product result is empty, ask one clarifying question and offer human support.
```

## API Examples

Capture a lead:

```bash
curl -X POST https://kpskrishnai.com/api/omnichannel/leads \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "WHATSAPP",
    "externalUserId": "919175834000",
    "customerName": "Customer",
    "mobile": "+91 9175834000",
    "sourceCampaign": "ig_reel_wedding_collection",
    "productInterest": "pearl earrings under 2000",
    "messageText": "Show pearl earrings under 2000"
  }'
```

Search products:

```bash
curl "https://kpskrishnai.com/api/omnichannel/products/search?q=pearl%20earrings&maxPrice=2000&source=whatsapp&campaign=ig_reel_wedding_collection&coupon=WELCOME10"
```

Meta webhook verification:

```text
GET https://kpskrishnai.com/api/omnichannel/webhooks/meta?hub.mode=subscribe&hub.verify_token=<token>&hub.challenge=<challenge>
```

## EC2 Deployment Checklist

- Run the existing deploy flow so `backend/src/main/resources/schema.sql` applies the new tables.
- Confirm Nginx or load balancer routes `/api/*` to Spring Boot and all other routes to React.
- Open HTTPS only to the public internet; keep PostgreSQL private.
- Set `OMNICHANNEL_WEBSITE_BASE_URL=https://kpskrishnai.com`.
- Set `CORS_ALLOWED_ORIGINS` to the public website domains.
- Restart backend after environment changes.
- Test `GET /api/omnichannel/products/search?q=test` from the public domain.
- Test one generated `autoAdd` link with a real product id.
