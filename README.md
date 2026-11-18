# ✈️ AeroFlow — Airline Operations & Passenger Management Platform

A production-style, event-driven airline platform: **8 Spring Boot microservices** (Java 21) behind an API gateway,
PostgreSQL, Redis and Kafka, with a **React + TypeScript** SPA providing an airline **staff operations console**
and a **passenger booking & check-in portal**.

> Payments run against a mock payment gateway (no card is charged, no ticket is issued) and e-mail/SMS go through
> mock adapters until real providers are configured. Production seeds the flight schedule only; demo users and
> synthetic reporting history are local/dev-only.

## Highlights
- **Booking engine**: PNR generation, interactive seat map, Idempotency-Key protected checkout, mock PSP, cancellations with refunds.
- **No double booking**: per-flight `SELECT … FOR UPDATE`, in-lock re-check and a partial unique index — proven by a 10-way concurrent race test.
- **Online check-in**: 48h/60m window, seat changes, dangerous-goods declaration, PDF boarding pass with IATA-style BCBP QR code.
- **Event-driven**: transactional outbox → Kafka → idempotent consumers (notifications, passenger history, reporting read model, flight-disruption cascades).
- **Security**: JWT + rotating refresh tokens with reuse detection, BCrypt, RBAC across 6 roles, lockout, gateway rate limiting, audit trail.
- **Operations**: executive dashboard, live departures board, delay/cancel workflows that notify affected passengers, reports with CSV export.
- **Quality**: 90 backend tests + Testcontainers ITs, 53 frontend tests, Playwright E2E, 44-check API smoke test, coverage gates, GitHub Actions CI/CD.
- **Ops-ready**: Dockerfiles, Docker Compose (with Prometheus + Grafana), Kubernetes manifests, Render blueprint, Vercel config.

## Tech stack
**Frontend** React 18 · TypeScript · Vite · Tailwind CSS v4 · shadcn/ui-style Radix components · React Router · TanStack Query ·
React Hook Form · Zod · Recharts · Lucide · Vitest · Testing Library · Playwright
**Backend** Java 21 · Spring Boot 3.3 · Spring Security · Spring Data JPA/Hibernate · Spring Cloud Gateway · Spring Kafka ·
Flyway · Resilience4j · springdoc-openapi · Micrometer · JUnit 5 · Mockito · Testcontainers
**Infra** PostgreSQL · Redis · Apache Kafka (KRaft) · Docker · Docker Compose · Kubernetes · GitHub Actions · Prometheus · Grafana · Vercel · Render

## Repository layout
```
backend/            Maven multi-module: common, gateway, auth, flight, passenger, booking, checkin, notification, reporting
frontend/           React SPA (staff console under /admin, passenger portal at /), Vitest + Playwright tests, vercel.json
infra/              docker-compose.yml, db init, monitoring (Prometheus, Grafana), k8s/
scripts/            local-backend.sh, local-kafka.sh (Docker-free dev), smoke_test.py
docs/openapi/       OpenAPI 3 specs exported from the running services
.github/workflows/  CI/CD pipeline
```

## Quick start

### Option A — Docker Compose (everything, one command)
```bash
cp .env.example .env    # set JWT_SECRET (openssl rand -base64 48), DB_PASSWORD, POSTGRES_PASSWORD, GRAFANA_ADMIN_PASSWORD
cd infra && docker compose --env-file ../.env up -d --build
```
Open http://localhost:3000 (UI) · http://localhost:8080 (API) · http://localhost:3001 (Grafana) · http://localhost:9090 (Prometheus).

### Option B — native (no Docker): PostgreSQL + Redis installed locally, Java 21, Node 22+, pnpm
```bash
cp .env.example .env                               # fill JWT_SECRET, DB_PASSWORD
psql -d postgres -c "CREATE ROLE aeroflow LOGIN PASSWORD '<DB_PASSWORD>' CREATEDB"
psql -d postgres -f infra/db/init.sql              # creates the 7 service databases
for d in auth flight passenger booking checkin notification reporting; do psql -d postgres -c "ALTER DATABASE aeroflow_$d OWNER TO aeroflow"; done
scripts/local-kafka.sh &                           # single-node KRaft broker (Apache Kafka 3.8 tarball in ~/.aeroflow-dev)
scripts/local-backend.sh build && scripts/local-backend.sh start
scripts/local-backend.sh status                    # all 8 ports should report 200
pnpm --dir frontend install && pnpm --dir frontend dev   # http://localhost:5173
```

## Accounts
**Production:** the first SUPER_ADMIN is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD`; passengers self-register.
Demo accounts are never seeded (`SEED_DEMO_USERS=false`) and the demo-login list is hidden in production builds.

**Local development** (`SEED_DEMO_DATA=true`) — password for all: **`Aeroflow@2026`**

| Role | E-mail |
|---|---|
| Super admin | superadmin@aeroflow.dev |
| Admin | admin@aeroflow.dev |
| Operations manager | ops@aeroflow.dev |
| Flight agent | agent@aeroflow.dev |
| Check-in agent | checkin@aeroflow.dev |
| Passenger | passenger@aeroflow.dev |

Test cards: `4242 4242 4242 4242` (approved), `4000 0000 0000 0002` (declined), any future expiry, any CVV.

## Tests
```bash
cd backend && ./mvnw verify                       # 90 tests + Testcontainers ITs (Docker) + JaCoCo gates
cd frontend && pnpm coverage && pnpm e2e           # 53 Vitest tests + 4 Playwright journeys
python3 scripts/smoke_test.py http://localhost:8080   # 44 end-to-end API checks incl. concurrent seat race
```

## Documentation
[ARCHITECTURE](ARCHITECTURE.md) · [API](API_DOCUMENTATION.md) · [DATABASE](DATABASE_DESIGN.md) · [DEPLOYMENT](DEPLOYMENT.md) ·
[TESTING](TESTING.md) · [SECURITY](SECURITY.md) · [PROJECT STATUS](PROJECT_STATUS.md)

## Known limitations
- JWTs are HS256 with a shared secret (RS256/JWKS is the documented upgrade path).
- Refresh token stored in `localStorage` (trade-off for a cross-origin Vercel ↔ API deployment; see SECURITY.md).
- Payments, e-mail and SMS are mock adapters; no real money or messages move.
- Seat holds are not time-boxed: a seat is reserved atomically at payment rather than held during checkout.
- Single currency (AED) and simplified fares (one economy and one business fare per flight).
- Reporting revenue includes synthetic demo history (flagged `synthetic` in data and labelled in the UI).
