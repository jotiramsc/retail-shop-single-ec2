# Gupshup WhatsApp Template Catalog

Use these templates in Gupshup for the Krishnai production WhatsApp sender.

App configuration already supports Gupshup template IDs through AWS Secrets Manager. After a template is approved, copy its Gupshup template ID into the matching secret key below.

## Sender Configuration

- Gupshup app name: `KPSKrishnai`
- Source number: `918830461523`
- Bot webhook URL: `https://kpskrishnai.com/api/whatsapp/webhook`
- OTP template currently configured: `GUPSHUP_OTP_TEMPLATE_ID`

## Authentication

### Customer Login OTP

- Suggested template name: `kps_customer_login_otp`
- Gupshup secret key: `GUPSHUP_OTP_TEMPLATE_ID`
- Category: `AUTHENTICATION`
- Language: English
- Variables: `{{1}}` OTP, `{{2}}` purpose, `{{3}}` OTP for button/url, `{{4}}` purpose for button/url
- Body:

```text
Your OTP for {{2}} is {{1}}. Valid for 5 minutes. Do not share it with anyone.
```

## Order Templates

### Order Confirmation

- Suggested template name: `kps_order_confirmation`
- Gupshup secret key: `GUPSHUP_ORDER_CONFIRMATION_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id, `{{3}}` amount, `{{4}}` estimated delivery date
- Body:

```text
Hi {{1}}, your order {{2}} has been successfully placed.
Total Amount: {{3}}
Estimated Delivery: {{4}}
Thank you for shopping with us.
```

### Order Dispatched

- Suggested template name: `kps_order_dispatched`
- Gupshup secret key: `GUPSHUP_ORDER_DISPATCHED_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id, `{{3}}` tracking id, `{{4}}` tracking url
- Body:

```text
Hi {{1}}, your order {{2}} has been dispatched.
Tracking ID: {{3}}
Track here: {{4}}
```

### Order Delivered

- Suggested template name: `kps_order_delivered`
- Gupshup secret key: `GUPSHUP_ORDER_DELIVERED_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id
- Body:

```text
Hi {{1}}, your order {{2}} has been delivered successfully.
Thank you for shopping with us.
```

### Order Cancelled

- Suggested template name: `kps_order_cancelled`
- Gupshup secret key: `GUPSHUP_ORDER_CANCELLED_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id
- Body:

```text
Hi {{1}}, your order {{2}} has been cancelled.
Please contact support if you need help.
```

### Order Returned

- Suggested template name: `kps_order_returned`
- Gupshup secret key: `GUPSHUP_ORDER_RETURNED_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id
- Body:

```text
Hi {{1}}, your return request for order {{2}} has been received.
We will update you shortly.
```

### Refund Initiated

- Suggested template name: `kps_refund_initiated`
- Gupshup secret key: `GUPSHUP_REFUND_INITIATED_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id, `{{3}}` amount
- Body:

```text
Hi {{1}}, refund for order {{2}} has been initiated.
Refund Amount: {{3}}
```

### Payment Failed

- Suggested template name: `kps_payment_failed`
- Gupshup secret key: `GUPSHUP_PAYMENT_FAILED_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id, `{{3}}` amount
- Body:

```text
Hi {{1}}, payment for order {{2}} was not completed.
Amount: {{3}}
Please try again from the website.
```

### Payment Success

- Suggested template name: `kps_payment_success`
- Gupshup secret key: `GUPSHUP_PAYMENT_SUCCESS_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id, `{{3}}` amount
- Body:

```text
Hi {{1}}, payment for order {{2}} was successful.
Amount: {{3}}
We will update you when your order is dispatched.
```

## Bot Templates

These are mainly for starting or resuming conversations outside the 24-hour WhatsApp customer-service window. Inside the window, the application sends normal bot text replies.

### Bot Welcome

- Suggested template name: `kps_bot_welcome`
- Gupshup secret key: `GUPSHUP_BOT_WELCOME_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name
- Body:

```text
Hi {{1}}, welcome to our service.
How can we help you today?
Reply with product category, budget, or order number.
```

### Bot Menu

- Suggested template name: `kps_bot_menu`
- Gupshup secret key: `GUPSHUP_BOT_MENU_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name
- Body:

```text
Hi {{1}}, please choose what you need:
1. Track Order
2. Talk to Support
3. Return Order
4. FAQs
You can also type a product request like "earrings under 2000".
```

### Support Escalation

- Suggested template name: `kps_support_escalation`
- Gupshup secret key: `GUPSHUP_SUPPORT_ESCALATION_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name
- Body:

```text
Hi {{1}}, our support team has been notified.
Someone from the store will contact you soon.
```

### Out Of Office

- Suggested template name: `kps_out_of_office`
- Gupshup secret key: `GUPSHUP_OUT_OF_OFFICE_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name
- Body:

```text
Hi {{1}}, our team is currently away.
Please leave your query and we will respond as soon as possible.
```

### Feedback Request

- Suggested template name: `kps_feedback_request`
- Gupshup secret key: `GUPSHUP_FEEDBACK_REQUEST_TEMPLATE_ID`
- Category: `UTILITY`
- Variables: `{{1}}` customer name, `{{2}}` order id
- Body:

```text
Hi {{1}}, your order {{2}} was delivered.
Please share your feedback with us. It helps us serve you better.
```

## Bot Test Messages

After the webhook is configured in Gupshup, send these messages from your mobile to the WhatsApp number:

```text
Hi
```

```text
Show earrings under 2000
```

```text
Show bangles between 500 and 1500
```

```text
Bridal jewellery
```

```text
Track order ORD-TEST-1
```

```text
Talk to support
```
