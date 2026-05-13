# WhatsApp Bot Setup

This application can run WhatsApp bot replies, OTP delivery, and order notifications through Gupshup or Meta. Production is currently configured to prefer Gupshup.

## Required Secrets

Use these Gupshup secrets for OTP and bot communication:

- `WHATSAPP_PROVIDER`: `GUPSHUP`
- `GUPSHUP_API_KEY`: Gupshup app API key
- `GUPSHUP_APP_NAME`: `KPSKrishnai`
- `GUPSHUP_SOURCE_NUMBER`: `918830461523`
- `GUPSHUP_OTP_TEMPLATE_ID`: approved login OTP template ID

For order and proactive bot templates, add approved Gupshup template IDs from [gupshup-whatsapp-template-catalog.md](./gupshup-whatsapp-template-catalog.md):

- `GUPSHUP_ORDER_CONFIRMATION_TEMPLATE_ID`
- `GUPSHUP_ORDER_DISPATCHED_TEMPLATE_ID`
- `GUPSHUP_ORDER_DELIVERED_TEMPLATE_ID`
- `GUPSHUP_ORDER_CANCELLED_TEMPLATE_ID`
- `GUPSHUP_ORDER_RETURNED_TEMPLATE_ID`
- `GUPSHUP_REFUND_INITIATED_TEMPLATE_ID`
- `GUPSHUP_PAYMENT_FAILED_TEMPLATE_ID`
- `GUPSHUP_PAYMENT_SUCCESS_TEMPLATE_ID`
- `GUPSHUP_BOT_WELCOME_TEMPLATE_ID`
- `GUPSHUP_BOT_MENU_TEMPLATE_ID`
- `GUPSHUP_SUPPORT_ESCALATION_TEMPLATE_ID`
- `GUPSHUP_OUT_OF_OFFICE_TEMPLATE_ID`
- `GUPSHUP_FEEDBACK_REQUEST_TEMPLATE_ID`

Meta Cloud API secrets are only needed if `WHATSAPP_PROVIDER=META`:

- `META_ACCESS_TOKEN`: Meta token with `whatsapp_business_messaging`.
- `META_GRAPH_VERSION`: `v25.0`.
- `META_WHATSAPP_PHONE_NUMBER_ID`: test phone number id.
- `OMNICHANNEL_WEBHOOK_VERIFY_TOKEN`: any strong string copied into Meta webhook verification.

## Webhook

Configure the Gupshup inbound/callback webhook URL:

```text
https://kpskrishnai.com/api/whatsapp/webhook
```

For Meta Cloud API only, the verify token is:

```text
value of OMNICHANNEL_WEBHOOK_VERIFY_TOKEN
```

Gupshup does not use the Meta verification challenge on this endpoint; configure the same callback URL for inbound message events in the Gupshup dashboard.

## Test Bot With Postman

Use this direct test payload without Meta:

```http
POST https://kpskrishnai.com/api/whatsapp/webhook
Content-Type: application/json

{
  "from": "918390968506",
  "name": "Test Customer",
  "text": "Show earrings under 2000",
  "messageId": "wamid.local-test"
}
```

Order tracking test:

```http
POST https://kpskrishnai.com/api/whatsapp/webhook
Content-Type: application/json

{
  "from": "918390968506",
  "name": "Test Customer",
  "text": "Track order ORD-20260511123456",
  "messageId": "wamid.local-test-order"
}
```

## Test Template Sender

After staff login, call:

```http
POST https://kpskrishnai.com/api/whatsapp/templates/send-test
Authorization: Bearer <staff-jwt>
Content-Type: application/json

{
  "mobile": "918390968506",
  "templateKey": "ORDER_CONFIRMATION",
  "variables": ["Customer", "ORD-TEST-1", "Rs. 999.00", "16 May 2026"]
}
```

The service uses Gupshup template IDs when `WHATSAPP_PROVIDER=GUPSHUP`. If a Gupshup template ID is missing for a non-OTP template, it falls back to a session text message. That fallback works only inside an active WhatsApp customer-service window.

OTP does not fall back to showing the code on screen in production. If OTP fails, check the Gupshup template ID, destination opt-in, and Gupshup app logs.

## Template Keys

Supported template keys:

- `ORDER_CONFIRMATION`
- `ORDER_DISPATCHED`
- `ORDER_DELIVERED`
- `ORDER_CANCELLED`
- `ORDER_RETURNED`
- `REFUND_INITIATED`
- `PAYMENT_FAILED`
- `PAYMENT_SUCCESS`
- `LOGIN_OTP`
- `BOT_WELCOME`
- `BOT_MENU`
- `SUPPORT_ESCALATION`
- `OUT_OF_OFFICE`
- `FEEDBACK_REQUEST`

Meta error `#132001` means the sender phone number id is connected to a WABA that does not have that template name/language approved.
