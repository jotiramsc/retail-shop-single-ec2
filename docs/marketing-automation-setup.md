# Marketing Automation Setup

This module creates AI-assisted social campaigns for Instagram, Facebook, and WhatsApp, but it never auto-posts without owner/admin approval.

For social-to-website lead capture, product recommendation links, Meta webhook setup, and n8n workflow configuration, see `docs/omnichannel-commerce-configuration.md`.

## What this module needs

### 1. OpenAI
Used for caption, hashtag, CTA, and image prompt generation.

Required:
- `MARKETING_AI_ENABLED=true`
- `MARKETING_OPENAI_API_KEY`
- optional: `MARKETING_OPENAI_MODEL`
- optional: `MARKETING_OPENAI_IMAGE_MODEL`
- optional: `MARKETING_OPENAI_IMAGE_SIZE`
- optional: `MARKETING_OPENAI_IMAGE_QUALITY`

If the API key is missing, the app falls back to safe mock AI drafts.

### 2. Meta (Instagram + Facebook)
Used for real publishing to Instagram and Facebook through the Meta Graph API.

Required:
- `MARKETING_META_ACCESS_TOKEN` for Instagram publishing
- `MARKETING_INSTAGRAM_BUSINESS_ACCOUNT_ID` for Instagram publishing
- `FB_PAGE_ID` or `MARKETING_FACEBOOK_PAGE_ID` for Facebook publishing
- `FB_PAGE_ACCESS_TOKEN` or `MARKETING_FACEBOOK_PAGE_ACCESS_TOKEN` for Facebook publishing
- optional: `MARKETING_META_GRAPH_VERSION`

Without these values, Instagram/Facebook publishing stays in placeholder mode and will fail with a clear log message.

The Facebook token must be a Page access token for the configured Page and must have publish access, including `pages_manage_posts` and the related Page read permissions. A WhatsApp-only Meta token cannot publish to Facebook.

### 3. Meta WhatsApp Cloud API

Used for WhatsApp campaign publishing after owner/admin approval.

Required:
- `META_ACCESS_TOKEN`
- `META_WHATSAPP_PHONE_NUMBER_ID`
- `MARKETING_WHATSAPP_PHONE_NUMBER_ID`

OTP also requires an approved authentication template:
- `META_WHATSAPP_OTP_TEMPLATE_NAME`
- `META_WHATSAPP_OTP_TEMPLATE_LANGUAGE`

### Important
Marketing messages outside the 24-hour customer-service window must use approved Meta WhatsApp templates and customer opt-in.

## Approval roles

- `ADMIN` can create, edit, and generate campaigns
- `OWNER` can approve, reject, schedule, and publish
- staff access is controlled through the `MARKETING_AUTOMATION` permission

## Seed data

The local seed includes one example campaign:
- `Akshaya Tritiya Jewellery Glow`

It creates pending-approval draft content for:
- Instagram
- Facebook
- WhatsApp

## Publishing behavior

- AI generation creates drafts only
- drafts move into `PENDING_APPROVAL`
- only `APPROVED` content can be scheduled or published
- scheduler checks due `SCHEDULED` content every minute
- every publish attempt is stored in `publish_logs`
- analytics rows are created for published content and can be extended with real platform metrics later
