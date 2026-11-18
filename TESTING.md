# AeroFlow — Testing

| Layer | Tooling | Where | Count |
|---|---|---|---|
| Backend unit & service | JUnit 5, Mockito, AssertJ | `backend/*/src/test/java` | 90 tests |
| Backend controller / security | `@WebMvcTest` + real `SecurityConfig` + real JWTs | booking, flight | (included above) |
| Backend repository integration | Testcontainers PostgreSQL + Flyway schema | `booking-service/.../SeatAssignmentRepositoryIT` | 3 tests (need Docker) |
| Frontend unit & component | Vitest, React Testing Library, user-event | `frontend/src/**/*.test.ts(x)` | 53 tests |
| End-to-end UI | Playwright (Chromium desktop + Pixel 7) | `frontend/e2e` | 4 tests |
| End-to-end API | Python stdlib smoke test against the gateway | `scripts/smoke_test.py` | 44 checks |

## Commands

```bash
cd backend && ./mvnw verify          # unit + controller tests, Testcontainers ITs (if Docker), JaCoCo report + gate
cd frontend && pnpm test             # Vitest
cd frontend && pnpm coverage         # Vitest + v8 coverage + per-file thresholds
cd frontend && pnpm e2e              # Playwright (needs the backend running; starts Vite itself)
python3 scripts/smoke_test.py http://localhost:8080   # full-stack API checks
```

JaCoCo HTML: `backend/<service>/target/site/jacoco/index.html` · Vitest HTML/lcov: `frontend/coverage/`.

## What is covered

* **Booking & seats**: seat geometry, auto-allocation, cabin mismatch, duplicate seat in one request, occupied seat,
  full cabin, inventory exhaustion, idempotent replay (no second charge), idempotency-key reuse with a different body,
  declined payment persists nothing, booking closed < 45 min before departure, guest cancellation with surname check,
  seat release + refund + event on cancel, checked-in bookings protected. **Real concurrency**: 10 parallel requests for the
  same seat → exactly one `201` and nine `409` (smoke test, real PostgreSQL). Partial unique index verified with Testcontainers.
* **Auth**: token issue/verify, expired/foreign-issuer/foreign-key tokens rejected, weak secret refused, lockout after N failures,
  refresh rotation, reuse detection revoking the family, register conflict, password change revoking sessions.
* **RBAC**: 401 vs 403 vs 200 per role on staff, admin and `/internal/**` endpoints; tampered JWT rejected; client route guards.
* **Events**: outbox relay ordering and failure handling, envelope round-trip, inbox de-duplication, flight cancellation cascading to
  booking cancellations/refunds, delay notices fan-out, notification templates and channel failure handling, passenger travel history projection.
* **Frontend**: all Zod schemas (Luhn, expiry, password policy, routes, flight form refinements), API client (bearer, idempotency
  header, error normalisation, single-flight refresh + retry, network errors), SeatMap (selection rules, a11y labels, arrow-key navigation),
  login/register forms, route guards.
* **E2E**: passenger login → search → 2-traveller booking with seat selection → payment validation → confirmation → retrieve →
  online check-in → PDF boarding pass download; check-in agent RBAC; admin flight filters + status dialog; mobile layout without overflow.

## Measured coverage (honest numbers, last local run)

Backend line coverage (JaCoCo, unit tests only — ITs did not run locally because Docker is not installed on the dev machine):

| Module | Line coverage | Gate |
|---|---|---|
| booking-service | 71% (service 71%, domain 81%) | ≥ 65% |
| checkin-service | 73% (service 79%) | ≥ 65% |
| notification-service | 60% (service 72%) | ≥ 50% |
| flight-service | 57% (service 61%, domain 90%) | ≥ 50% |
| auth-service | 49% (service 50%, domain 83%) | ≥ 45% |
| common | 34% (security/web/error code is exercised by other modules' tests, which JaCoCo attributes per module) | — |
| passenger-service | 32% | — |
| reporting-service | 8% (SQL projections are verified by the smoke test, not unit tests) | — |

Frontend (Vitest v8): overall statements 24.8% (pages are exercised by Playwright rather than unit tests). Critical modules:
`SeatMap.tsx` 100% lines, `schemas.ts` 100%, `api.ts` 98.9%, `auth.tsx` 88.5% — enforced with per-file thresholds.

The goal of 100% unit coverage on critical business logic is **not yet met**; the gates lock in current levels so CI fails on regressions.
Next targets: SeatService (seat change paths), UserManagementService, reporting SQL via Testcontainers, passenger CRUD.

## CI quality gates (`.github/workflows/ci.yml`)
1. `backend`: `mvn verify` with Docker available → Testcontainers ITs run; JaCoCo per-module floors.
2. `frontend`: `tsc --noEmit`, Vitest with coverage thresholds, production build.
3. `e2e`: PostgreSQL + Redis service containers, Kafka container, all 8 services, API smoke test, Playwright.
4. `images`: builds and pushes all service images to GHCR on `main` after everything is green.
