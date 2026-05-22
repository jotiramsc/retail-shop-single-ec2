# CURRENT TASK

## Current Task: Customer Intelligence CRM Workspace

Status: Implemented locally, verified, deployed to EC2, and live-checked.

Scope:
- Customer CRM was redesigned into a spacious green/gold customer intelligence workspace with a sticky search/filter command bar, left customer list, and right customer 360 workspace.
- Customer dropdown search now returns recent customers for empty focused searches, so Billing/Reports customer dropdowns are not blank before typing.
- Customer login history display now uses the backend `loginTime` field and activity capture accepts both `category/page` and older `selectedCategory/sourcePage` payloads.
- Product search, product click, product view, add-to-cart, wishlist, order placed, and CRM support-chat events now feed customer activity intelligence with richer payloads.
- Customer details now expose gender, customer since, last active, preferred brands, shopping interests, search history, engagement score, sentiment, purchase prediction, churn risk, recommended products, and high-value badge.
- Support chat now seeds the conversation with the Krishnai greeting and includes a conversational onboarding flow for DOB, anniversary, language, favorite categories, budget, and shopping interests.
- Customer preferences remain editable, but the first profile collection experience is conversational rather than a large static form.

Verification:
- Backend `./mvnw test` passed: 77 tests.
- Frontend `npm run build` passed.
- `git diff --check` passed.
- Local Playwright desktop check passed for `/app/customers` with a QA staff session; no obvious text/control overlap was detected in the main viewport.

Deployment:
- EC2 release `local-20260521215711` deployed successfully.
- The first candidate exposed a missing additive DB column (`customer_activity_history.clicked_product`) while production stayed on the previous healthy release.
- Applied the additive CRM intelligence columns on EC2, reran the clean deploy, and promoted the release.
- Live checks passed: `/actuator/health` returned `UP`; `/app/customers` returned HTTP 200.
