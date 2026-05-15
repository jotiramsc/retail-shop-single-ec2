# CURRENT TASK

## TASK-17: Support Page Product Suggestion and WhatsApp Forwarding

Status: Completed

Scope:
- Allow the single-agent support inbox to search inventory and forward selected products to the customer's WhatsApp chat.
- Reuse existing inventory/product data and existing WhatsApp sender integration.
- Do not create duplicate products or new campaign/support modules.

Implementation:
- Support product picker searches loaded inventory by product name, category, SKU/item code, and simple price filters such as `under 2000`, `above 1000`, or `1000-3000`.
- Product picker cards show image, name, category, SKU, price/offer price, stock status, and quantity.
- Send button is labeled "Send to WhatsApp".
- Out-of-stock products show a warning and require confirmation before sending.
- WhatsApp product suggestion includes image when a public image URL exists, product name, price/offer price, stock, product link, and "Suggested by Krishnai support team".
- Conversation history stores productId, product name, sentBy, timestamp, customer mobile, provider message id, and WhatsApp send status in message metadata.
- Support chat displays product send status in the message history.
