# AeroFlow — Project Status

_Last updated: 2026-11-18_

## Phase tracker

| Phase | Scope | Status | Verification |
|---|---|---|---|
| 1 | Repo scaffold, architecture, design system, local infra | ✅ Done | Maven multi-module + Vite app build; local Postgres/Redis/Kafka |
| 2 | Authentication, authorization, user management | ✅ Done | JWT + rotating refresh, lockout, RBAC; unit + WebMvc tests; smoke test |
| 3 | Flight management (API + screens) | ✅ Done | CRUD, status workflow, search/filter/sort/paginate; tests + E2E |
| 4 | Passenger management & booking | ✅ Done | Profiles, travel history read model, booking wizard, PNR, mock payments |
| 5 | Seat selection, concurrency, check-in | ✅ Done | 10-way seat race → 1 winner; online/agent check-in; PDF boarding pass |
| 6 | Kafka events & notifications | ✅ Done | Outbox + idempotent consumers; e-mail/SMS mock adapters; delay fan-out verified |
| 7 | Dashboard, reporting, audit | ✅ Done | KPIs/charts/alerts, 4 reports + CSV, audit log |
| 8 | Automated tests, security checks, quality gates | ✅ Done (coverage below 100% target) | 90 + 53 unit tests, 3 ITs, 4 E2E, 44 API checks; JaCoCo + Vitest gates |
| 9 | Docker, CI/CD, Kubernetes, monitoring | ✅ Prepared | Compose/k8s/CI YAML validated; **Docker images not built locally (Docker not installed)** |
| 10 | Deploy backend + Vercel frontend | ⏳ Blocked on credentials | See "Needed to deploy" below |

## Verified locally (2026-11-18, macOS, native PostgreSQL 17 + Redis 8 + Kafka 3.8.1 KRaft)
- Backend: `./mvnw verify` → BUILD SUCCESS, **90 tests passed**, 3 Testcontainers ITs **skipped** (no Docker on this machine; they run in CI).
- All 8 services start and report healthy (`/actuator/health` 200 on 8080–8087); Swagger UI served; OpenAPI specs exported.
- `scripts/smoke_test.py` → **ALL 44 CHECKS PASSED** (auth/RBAC, refresh reuse detection, flight CRUD rules, idempotent booking,
  declined payment, concurrent seat race, retrieve, check-in, PDF, Kafka notifications, delay fan-out, dashboard, CSV, audit).
- Frontend: `pnpm typecheck` clean, `pnpm build` succeeds, **53 Vitest tests passed**, coverage thresholds met.
- Playwright: **4/4 passed** (full passenger journey, RBAC, admin flights, mobile layout).

## Coverage (measured)
Backend lines: booking 71% · check-in 73% · notification 60% · flight 57% · auth 49% · passenger 32% · reporting 8% · common 34%.
Frontend: overall 24.8% statements; SeatMap 100%, schemas 100%, API client 98.9%, auth 88.5%. Details in [TESTING.md](TESTING.md).

## Needed to deploy (Phase 10)
1. **GitHub**: `gh auth login` (or a repo URL + push access) to create the repository and enable Actions.
2. **Backend host**: Render account (Blueprint in `render.yaml`) — or Railway/Fly/another container host.
3. **Managed Kafka**: Confluent Cloud / Aiven / Redpanda Cloud bootstrap URL + SASL credentials.
4. **Vercel**: project access to deploy `frontend/` and set `VITE_API_BASE_URL` to the gateway URL.

## Outstanding / next
- Raise unit coverage on SeatService seat-change paths, UserManagementService, PassengerService and reporting SQL (Testcontainers).
- Run the Docker Compose stack and image builds on a machine with Docker (CI does this on every push).
- Time-boxed seat holds during checkout; RS256/JWKS; MFA for staff.

## Local environment notes
The development Mac had ~2 GB free disk and 8 GB RAM, so services run in "thin" classpath mode (`scripts/local-backend.sh build`)
instead of fat jars, with 256 MB heaps. Docker was not available, so containers were validated statically, not executed.
