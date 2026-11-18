# AeroFlow — Security

## Authentication
* **Passwords**: BCrypt (cost 12). Policy: 10–72 chars with upper, lower, digit and symbol (enforced server- and client-side).
* **Access tokens**: JWT HS256, 15-minute TTL, claims `sub, email, name, roles, iss=aeroflow-auth, jti`. Verified by every
  service (defense in depth — the gateway is not the only check). Secret from `JWT_SECRET` (≥ 32 bytes, startup fails otherwise).
* **Refresh tokens**: 256-bit random, stored only as SHA-256 hashes, 7-day TTL, **rotated on every use**. Presenting an
  already-rotated token revokes the entire token family (stolen-token detection). Logout, password change, password
  reset and account disable revoke sessions.
* **Brute force**: 5 failed logins → 15-minute lockout; constant-time path for unknown e-mails; gateway rate limit on
  `/api/auth/login|register|refresh` (Redis token bucket, per client IP).
* **Service-to-service**: short-lived JWTs with `ROLE_SERVICE`; `/internal/**` requires that role and is blocked at the gateway.

## Authorization
Role-based access control with `@PreAuthorize` on every staff endpoint (see roles table in API_DOCUMENTATION.md).
Ownership checks for passenger resources (own bookings / profile). Guest booking access requires PNR **and** passenger
surname, and returns 404 (not 403) on mismatch to avoid PNR enumeration. Only SUPER_ADMIN can grant or modify
SUPER_ADMIN; admins cannot disable themselves or drop their own admin role. The SPA mirrors the same permission map
for navigation and route guards, but the server is authoritative.

## Input handling
* Bean Validation on every request DTO (formats for PNR, seat, flight number, phone, passport, ISO country, card data).
* JPA/Criteria API and parameterised `JdbcTemplate` queries only — no string-concatenated SQL (the audit search builds
  its `WHERE` clause from fixed fragments with bound parameters). Sort fields are allow-listed.
* Consistent `ApiError` responses; stack traces never returned (`server.error.include-stacktrace=never`).
* CSV export neutralises spreadsheet formula injection (`=`, `+`, `-`, `@` prefixes).

## Data protection
* No full card number or CVV is stored or logged — only brand, last 4 and the mock PSP reference. The mock PSP logs masked data.
* Passport numbers are masked in booking/manifest responses; notification logs mask recipients.
* Secrets only via environment variables; `.env` is git-ignored; `.env.example` documents every variable without values.
  Demo accounts are seeded only when `SEED_DEMO_DATA=true` (disable outside demos).

## Transport & browser
* CORS terminated at the gateway with an explicit origin allow-list (`CORS_ALLOWED_ORIGINS`, optional patterns for Vercel previews);
  credentials are not used (bearer tokens), so cookies cannot be abused cross-site (no CSRF surface).
* API responses: CSP `default-src 'self'`, `X-Frame-Options: DENY`, `X-Content-Type-Options`; frontend (Vercel/nginx): HSTS,
  `nosniff`, `DENY` framing, strict referrer policy, restrictive permissions policy.
* **Trade-off**: the refresh token is kept in `localStorage` so sessions survive reloads on a cross-origin (Vercel → API) deployment
  where third-party cookies are unreliable. Mitigations: short access TTL, rotation + reuse detection, CSP, no `dangerouslySetInnerHTML`.
  With a same-site API domain, move it to an `HttpOnly; Secure; SameSite=Strict` cookie.

## Containers & runtime
Non-root users in all images, read-only root filesystem and dropped capabilities in Kubernetes, resource limits, liveness/readiness probes.

## Hardening roadmap
RS256 + JWKS key rotation · secrets manager (Vault / cloud KMS) · OWASP dependency-check and container scanning in CI ·
WAF in front of the gateway · field-level encryption of passport numbers · MFA for staff accounts.

## Reporting a vulnerability
Please open a private security advisory on the repository rather than a public issue.
