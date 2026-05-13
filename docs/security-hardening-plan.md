# Security Hardening Plan

This document captures the current security posture of the retail app and the hardening work we should do later.

It is based on the live single-EC2 deployment and the current codebase as of 2026-04-29.

## Goal

Protect the app so unwanted users cannot:

- access backend code or secrets
- log into admin or customer flows without authorization
- reach the EC2 machine directly
- reach the PostgreSQL database directly
- spoof payment callbacks
- reuse stored credentials or OTPs

## Current Security Posture

### What is already good

- Staff passwords are stored as BCrypt hashes.
- App and database secrets are stored in AWS Secrets Manager and written to local env files with `chmod 600`.
- PostgreSQL is not exposed on a public host port in the current single-EC2 deploy flow.
- The EC2 instance requires IMDSv2 (`HttpTokens: required`).
- The S3 artifact bucket has encryption, versioning, and public access block enabled.
- The customer OTP flow stores OTP hashes rather than plain OTP values.

### What is important to understand

- The frontend and admin app JavaScript bundles are public browser assets. That is normal for web apps.
- The backend Java source code is not publicly served by the application.
- If the EC2 host is compromised, the local PostgreSQL database and runtime secrets are also at risk because they live on the same machine.

## Confirmed Gaps

### 1. EC2 is directly reachable from the internet

Current stack design gives the EC2 instance:

- a public IP / Elastic IP
- public subnet placement
- direct ingress on ports `80` and `443`

This means traffic can bypass the ALB and hit the instance directly.

Why this matters:

- direct attack surface on the host
- harder to enforce one clean entry path
- weakens the value of ALB-only controls

## 2. Staff login uses Basic Auth stored in browser localStorage

Current frontend behavior:

- builds `Basic base64(username:password)`
- stores it in browser `localStorage`
- reuses it on admin requests

Why this matters:

- browser storage compromise leaks reusable staff credentials
- shared machine risk is higher
- XSS impact becomes much worse

## 3. Payment webhook endpoints are publicly open without real verification

Current webhook controller accepts callback payloads but does not verify:

- Razorpay signature

Why this matters:

- spoofed payment callbacks become possible
- payment state could be faked or poisoned if later logic trusts these events

## 4. Customer OTP is exposed in API responses

Current customer auth flow returns the generated OTP in the response body as `devOtp`, even when provider delivery succeeds.

Why this matters:

- removes the main security benefit of OTP
- anyone with response access can bypass message delivery

## 5. No visible brute-force protection for staff login

Current staff login path does not appear to have:

- rate limiting
- temporary lockout
- IP-based throttling

Why this matters:

- `/api/auth/login` can be brute-forced

## 6. Missing stronger browser security headers

Current live responses include a few basics, but we should still add:

- `Strict-Transport-Security`
- `Content-Security-Policy`
- `Referrer-Policy`
- `Permissions-Policy`

Why this matters:

- improves HTTPS enforcement
- reduces XSS blast radius
- tightens browser behavior

## 7. Customer JWT lifetime is too long

Current config uses a long customer token lifetime.

Why this matters:

- leaked customer tokens remain usable too long

## 8. Public health endpoint is acceptable, but should stay minimal

`/actuator/health` is public right now.

That is fine if it only returns minimal status data, but we should keep it that way and avoid exposing extra actuator endpoints.

## Recommended Hardening Plan

### Phase 1 - High Priority

These are the first changes to make.

1. Put EC2 behind the ALB only
   - move EC2 to a private subnet
   - remove the public IP / Elastic IP
   - allow app ingress only from the ALB security group
   - keep SSH closed; use SSM only

2. Replace staff Basic Auth
   - use short-lived signed staff JWTs or secure server sessions
   - do not store raw reusable credentials in browser localStorage

3. Remove `devOtp` from production
   - allow OTP-on-screen only in explicit local/dev mode
   - never expose real OTP in production API responses

4. Enforce webhook validation
   - verify Razorpay signatures
   - reject invalid or unsigned callbacks

5. Add staff login rate limiting
   - throttle `/api/auth/login`
   - add temporary lockout after repeated failures

### Phase 2 - Strongly Recommended

1. Add stronger security headers
   - HSTS
   - CSP
   - Referrer-Policy
   - Permissions-Policy

2. Shorten customer JWT lifetime
   - reduce TTL to a more reasonable production value
   - optionally add refresh flow if needed

3. Tighten CORS review
   - keep only required origins
   - avoid stale temporary domains

4. Review S3 write scope
   - keep the EC2 IAM role limited to only required buckets and actions

### Phase 3 - Nice to Have / Future

1. Add WAF in front of ALB
2. Add audit logging for admin sign-in and critical actions
3. Add anomaly alerts for repeated auth failures
4. Add database backup verification and restore drills
5. Separate database from app host in a future managed database setup

## Practical Answers

### Can unwanted users get my backend code?

Not through the website directly.

They can download the frontend JavaScript bundle, including the admin SPA bundle, because browser code must be sent to the browser. That is normal. No secrets should live in frontend code.

### Can unwanted users get access to my EC2 machine?

There is no public SSH rule open right now, which is good.

However, the machine is still directly reachable over the web because it has a public IP and public web ingress. That should be removed in the hardening phase.

### Can unwanted users get access to my database?

The PostgreSQL port is not intentionally published publicly in the current Docker deploy flow, which is good.

But because the database runs on the same EC2 machine, a machine-level compromise can still become a database compromise.

## Suggested Implementation Order

When we come back to this work later, do it in this order:

1. private EC2 behind ALB only
2. replace staff Basic Auth with staff JWT/session auth
3. remove production OTP echo
4. verify payment webhooks
5. add login throttling
6. add security headers
7. shorten customer token lifetime

## Notes

- This document is intentionally practical and tied to the current deployment shape.
- Revisit it if the app moves from single EC2 to App Runner, ECS, or RDS.
