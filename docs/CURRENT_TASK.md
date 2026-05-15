# CURRENT TASK

## TASK-1: Campaign Lead Tracking Backend

Status: Completed

Scope:
- Capture campaignId, source, productId or offerId, sessionId, and timestamp.
- Accepted sources: facebook, instagram, whatsapp, direct.
- Save each lead visit in the backend.

Implementation:
- Public endpoint: POST /api/campaign-leads/visits
- Persists records to campaign_lead_visits.
- Request fields: campaignId, source, productId, offerId, sessionId, timestamp.

Out of scope:
- Campaign Studio analytics UI.
- WhatsApp bot category/menu/product changes.
- AI intent search changes.
