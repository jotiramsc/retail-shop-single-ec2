# SharePoint Requirements Implementation Prompt Pack

Access note: the two SharePoint Word links supplied in the request returned `401 Access denied` from this environment, so the actual document requirements still need to be pasted into the `Requirement Source` block below before running these prompts.

## Requirement Source

Paste the combined contents of both SharePoint documents here before using any prompt:

```text
TODO: Paste requirements from:
- https://calibousa-my.sharepoint.com/:w:/g/personal/sksingh_calibo_com/IQDk8kexR7kVQra-ToajCpu2ASpxSnSv08I5J3Nn_YL-8Lw
- https://calibousa-my.sharepoint.com/:w:/r/personal/sksingh_calibo_com/_layouts/15/Doc.aspx?sourcedoc=%7B4398553b-2077-44bf-8282-4feace4ae6ab%7D
```

## Shared Repo Context

Use this context in every implementation part:

- Backend is Spring Boot 3 / Java 17 under `backend/src/main/java/com/retailshop`.
- Frontend is React 18 / Vite under `frontend/src`.
- Main frontend API wrapper is `frontend/src/services/retailService.js`; do not add duplicate API clients.
- Reuse existing UI primitives where possible: `PageHeader`, `Panel`, `MetricCard`, `DataTable`, `StorefrontHeader`, and global styles.
- Reuse existing backend layers instead of creating parallel modules:
  - Product/catalog/inventory: `ProductService`, `ProductCategoryOptionService`, `ProductRepository`, `ProductController`, `Product`, `ProductResponse`.
  - Customer/profile/address/auth: `CustomerService`, `CustomerAuthService`, `CustomerProfileService`, `AddressService`, `CustomerJwtService`, `CustomerSecurity`.
  - Cart/checkout/order/payment: `CartService`, `CheckoutService`, `OrderService`, `PaymentService`, `RazorpayPaymentService`, `OrderPricingService`.
  - Billing/receipt/reporting: `BillingService`, `ReceiptSettingsService`, `ReportService`, `SalespersonSalesService`.
  - Offers/automation/marketing: `OfferService`, `AutomationService`, `MarketingAutomationService`, `AIContentGenerationService`, `SocialPublisher`, `WhatsAppMessageService`.
  - Site analytics: `SiteInteractionService`, `GeoLookupService`.
  - Staff/security: `StaffUserService`, `SecurityConfig`, `StaffRole`, `AppPermission`.
- Follow the existing controller -> service interface -> service impl -> repository pattern.
- Use existing DTO naming and validation style under `backend/src/main/java/com/retailshop/dto`.
- Add focused tests under `backend/src/test/java/com/retailshop` for backend logic and run `cd backend && ./mvnw test`.
- For frontend changes, run `cd frontend && npm run build` after implementation.

## Part 1 Prompt: Backend Foundation and API Contracts

```text
You are working in the existing Luxe Retail Studio repo. Read the pasted SharePoint requirements in the Requirement Source block and implement only the backend foundation needed for the first implementation slice.

Goal:
Create or extend the backend domain model, DTOs, repositories, service interface contracts, and controller endpoints required by the requirements, while reusing the existing classes and services listed in Shared Repo Context.

Scope:
- Read these existing files before editing: relevant controller(s), service interface(s), service impl(s), entity/entities, repository/repositories, DTOs, `SecurityConfig`, and any related tests.
- Extend existing entities/services/controllers where the feature belongs. Do not create duplicate flows such as a second product, checkout, customer, billing, marketing, or reporting stack.
- Add new DTOs/enums only when existing DTOs/enums cannot represent the requirement cleanly.
- Add repository methods using Spring Data naming or existing query style.
- Add controller routes under the existing API namespace that best matches the domain.
- Add validation annotations and consistent error handling using `BusinessException` / `ResourceNotFoundException` where appropriate.
- Keep business logic minimal in controllers; delegate to services.
- Preserve existing behavior and API compatibility.

Deliverables:
- Backend compiles.
- API contracts exist and map to service methods.
- Minimal happy-path service implementation is present where needed, without fake placeholder responses.
- Focused backend tests cover DTO/service/controller contract behavior that can be tested at this stage.
- Run `cd backend && ./mvnw test` and fix failures caused by your changes.

Do not implement frontend UI in this part.
```

## Part 2 Prompt: Backend Business Logic and Existing Service Integration

```text
You are working in the existing Luxe Retail Studio repo after Part 1. Read the pasted SharePoint requirements in the Requirement Source block and implement the complete backend business behavior for the second implementation slice.

Goal:
Wire the new requirement behavior into the existing service layer without bypassing established services.

Scope:
- Reuse existing services before adding new ones:
  - Use `OrderPricingService` for pricing/discount totals.
  - Use `OfferService` for coupon/offer behavior.
  - Use `CustomerService`, `CustomerProfileService`, and `AddressService` for customer data.
  - Use `CartService`, `CheckoutService`, `OrderService`, and `PaymentService` for checkout/order/payment flows.
  - Use `BillingService` and `ReportService` for invoice/report behavior.
  - Use `MarketingAutomationService`, `AIContentGenerationService`, `SocialPublisher`, and `WhatsAppMessageService` for campaign/messaging behavior.
  - Use `StaffUserService`, `StaffRole`, `AppPermission`, and existing Spring Security patterns for admin/staff permissions.
- Add transactional boundaries in service implementations where writes occur.
- Keep data mapping close to existing `map...Response` helper patterns.
- Handle edge cases from the SharePoint requirements: missing records, invalid state transitions, duplicate submissions, authorization, empty results, pagination, and date/time boundaries.
- Add or update unit tests for service behavior and failure paths.
- Update seed/local data only if the requirement needs demo-ready records.

Deliverables:
- Backend behavior matches the SharePoint requirements for this slice.
- Existing APIs continue to work.
- New failure modes return clear API errors.
- Run `cd backend && ./mvnw test` and fix failures caused by your changes.

Do not implement frontend UI in this part except for API shape notes in comments or docs if needed.
```

## Part 3 Prompt: Frontend Admin and Operational UI

```text
You are working in the existing Luxe Retail Studio repo after Parts 1 and 2. Read the pasted SharePoint requirements in the Requirement Source block and implement the admin/back-office frontend for the third implementation slice.

Goal:
Add the required admin or staff-facing UI using existing React patterns and the existing backend APIs.

Scope:
- Use `frontend/src/services/retailService.js` for all API calls; add functions there instead of calling Axios directly in pages.
- Reuse existing page/component patterns from nearby pages such as `ProductsPage.jsx`, `OffersPage.jsx`, `BillingPage.jsx`, `CampaignsPage.jsx`, `ReportsPage.jsx`, `OrdersPage.jsx`, and `UsersPage.jsx`.
- Reuse `PageHeader`, `Panel`, `DataTable`, and `MetricCard` where they fit.
- Add route/navigation changes in `App.jsx` only if required.
- Preserve existing auth and role behavior. Do not expose admin-only actions to customer-facing routes.
- Implement loading, empty, error, success, validation, and disabled states.
- Keep forms consistent with existing styling and validation helpers in `frontend/src/utils`.
- Avoid new UI libraries unless already present in `package.json`.

Deliverables:
- Admin/staff UI implements the SharePoint requirements for this slice.
- API calls are centralized in `retailService.js`.
- UI handles backend errors gracefully.
- Run `cd frontend && npm run build` and fix failures caused by your changes.

Do not modify unrelated customer-facing pages unless the requirement explicitly needs it.
```

## Part 4 Prompt: Customer Experience, QA, Reports, and Release Readiness

```text
You are working in the existing Luxe Retail Studio repo after Parts 1, 2, and 3. Read the pasted SharePoint requirements in the Requirement Source block and finish the fourth implementation slice: customer-facing behavior, reports/analytics, regression tests, and release readiness.

Goal:
Complete any customer-facing flows and operational hardening needed so the full requirement can be tested end to end.

Scope:
- If the requirement affects the public store, update the relevant existing pages: `PublicHomePage.jsx`, `PublicProductsPage.jsx`, `CartPage.jsx`, `CheckoutPage.jsx`, `CustomerLoginPage.jsx`, `CustomerProfilePage.jsx`, and `OrdersPage.jsx`.
- Use existing customer auth/cart helpers in `frontend/src/utils/auth.js`, `frontend/src/utils/cart.js`, and `frontend/src/utils/checkout.js`.
- If the requirement affects reporting, extend `ReportService`, `SalespersonSalesService`, report DTOs, and `ReportsPage.jsx` instead of adding a separate report stack.
- If messaging/publishing is involved, use existing WhatsApp/marketing publisher abstractions and keep approval gates intact.
- Add or update backend tests for end-to-end service behavior and report calculations.
- Add frontend build verification and manual test notes.
- Update docs only where useful, preferably under `docs/`.
- Remove temporary code, unused imports, and dead branches introduced during the implementation.

Deliverables:
- Full feature path works across backend and frontend.
- Backend tests pass with `cd backend && ./mvnw test`.
- Frontend build passes with `cd frontend && npm run build`.
- Provide a concise final summary listing files changed, tests run, remaining risks, and any environment variables or deployment changes required.
```
