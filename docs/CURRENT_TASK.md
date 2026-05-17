# CURRENT TASK

## Remaining Task Batch: Support, Cart Links, Reports, Profile, Icons

Status: Implemented and locally verified. Pending final EC2 deploy.

Scope:
- Complete remaining task gaps before one final deploy.
- Keep Campaign Studio source-wise lead tracking unchanged except existing delivery status fixes.
- Improve support product forwarding with clickable website/cart links.
- Add direct `/cart/add` storefront link handling.
- Complete admin customer detail display polish.
- Add signup/profile OTP/name polish.
- Add admin validation guardrails for inventory/category forms.
- Add dynamic category icon selection in inventory.
- Improve support chat UX and global unread alert.
- Improve WEBSITE order report ordering.

Verification:
- Backend `./mvnw test`: 65 tests passed.
- Frontend `npm run build`: passed.

Deployment:
- Not deployed yet for this batch. User requested all remaining tasks complete first, then deploy once.
