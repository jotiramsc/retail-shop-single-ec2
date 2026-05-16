# CURRENT TASK

## TASK-D: WhatsApp Greeting/Menu Fixes

Status: Completed

Scope:
- Fix WhatsApp greeting image/banner delivery.
- Remove duplicate greeting menu cards.
- Keep the greeting menu in one clean WhatsApp list card.
- Make Show More open the secondary options menu.

Implementation:
- Added the provided Krishnai greeting banner as `/assets/krishnai-whatsapp-greeting.png`.
- Greeting sends the banner image first, then one list card with View Collections, Offers, Track Order, and Show More.
- Greeting no longer sends separate quick-button and list menu cards together.
- Show More opens one list containing Talk to Shop, dynamic categories, My Cart, and Support.
- WhatsApp image-send null responses are handled safely before falling back to interactive/text delivery.
