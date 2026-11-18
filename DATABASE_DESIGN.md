# AeroFlow — Database Design

PostgreSQL 16+, one **database per service** (`aeroflow_auth`, `aeroflow_flight`, `aeroflow_passenger`,
`aeroflow_booking`, `aeroflow_checkin`, `aeroflow_notification`, `aeroflow_reporting`). No service reads another
service's tables. Schemas are created and versioned by **Flyway** (`<service>/src/main/resources/db/migration`), and
Hibernate runs with `ddl-auto=validate` so drift fails fast at startup. Every write service additionally owns an
`outbox_events` table (transactional outbox) and a `processed_events` table (idempotent inbox).

## ER diagrams

```mermaid
erDiagram
  USERS ||--o{ USER_ROLES : has
  USERS ||--o{ REFRESH_TOKENS : issues
  USERS { uuid id PK; varchar email "unique lower()"; varchar password_hash "BCrypt(12)"; bool enabled; int failed_attempts; timestamptz locked_until; bigint version }
  USER_ROLES { uuid user_id FK; varchar role }
  REFRESH_TOKENS { uuid id PK; uuid user_id FK; varchar token_hash "SHA-256, unique"; uuid family_id; timestamptz expires_at; timestamptz revoked_at }
```
*auth-service*

```mermaid
erDiagram
  AIRPORTS ||--o{ FLIGHTS : "origin / destination"
  AIRCRAFT ||--o{ FLIGHTS : operates
  AIRPORTS { varchar code PK "IATA"; varchar city; varchar timezone "IANA" }
  AIRCRAFT { uuid id PK; varchar registration UK; int business_rows; varchar business_layout "AC-DF"; int economy_rows; varchar economy_layout; varchar status }
  FLIGHTS { uuid id PK; varchar flight_number; varchar origin_code FK; varchar destination_code FK; uuid aircraft_id FK; timestamptz departure_time; timestamptz arrival_time; varchar status; int delay_minutes; numeric economy_fare; numeric business_fare; bigint version }
```
*flight-service* — `UNIQUE (flight_number, departure_time)`, `CHECK (origin <> destination)`, `CHECK (arrival > departure)`,
index on `(origin_code, destination_code, departure_time)` for search.

```mermaid
erDiagram
  BOOKINGS ||--|{ BOOKING_PASSENGERS : contains
  BOOKING_PASSENGERS ||--o{ SEAT_ASSIGNMENTS : "holds (history)"
  BOOKINGS ||--o{ PAYMENTS : "charge / refund"
  FLIGHT_INVENTORY ||..o{ BOOKINGS : "locked per flight"
  BOOKINGS { uuid id PK; varchar pnr UK; uuid flight_id; varchar status; numeric total_amount; varchar card_last4; varchar channel; bool reminder_sent; bigint version }
  BOOKING_PASSENGERS { uuid id PK; uuid booking_id FK; varchar cabin; varchar seat_number; bool checked_in }
  SEAT_ASSIGNMENTS { uuid id PK; uuid flight_id; varchar seat_number; uuid passenger_id FK; bool released }
  FLIGHT_INVENTORY { uuid flight_id PK; int capacity; int booked_seats "CHECK 0..capacity"; varchar status }
  PAYMENTS { uuid id PK; uuid booking_id FK; varchar type; numeric amount; varchar provider_ref }
  IDEMPOTENCY_KEYS { varchar idem_key PK; varchar request_hash; uuid booking_id }
```
*booking-service* — **`CREATE UNIQUE INDEX ux_seat_active ON seat_assignments (flight_id, seat_number) WHERE released = FALSE`**
is the database-level guarantee against double booking; released rows keep seat history. Partial index
`ix_bookings_reminder` serves the check-in reminder job.

```mermaid
erDiagram
  PASSENGERS { uuid id PK; uuid user_id UK; varchar email "unique lower()"; varchar passport_number; varchar loyalty_tier; int loyalty_points }
  TRAVEL_HISTORY { uuid id PK; varchar email; uuid booking_id; varchar pnr; varchar status; bool checked_in }
  CHECKINS { uuid id PK; uuid passenger_id UK; uuid flight_id; varchar boarding_pass_number UK; int sequence_number; varchar boarding_group }
  FLIGHT_SEQUENCES { uuid flight_id PK; int last_seq }
  NOTIFICATIONS { uuid id PK; uuid event_id; varchar channel; varchar recipient; text body; varchar status }
  AUDIT_LOG { uuid id PK; varchar actor_email; varchar action; varchar entity_type; varchar entity_id; varchar ip_address; timestamptz occurred_at }
```
*passenger, check-in, notification and reporting services* — `travel_history` and the `rpt_*` tables are **read models**
built from events. `CHECKINS UNIQUE (flight_id, sequence_number)` + atomic `INSERT … ON CONFLICT DO UPDATE … RETURNING`
on `flight_sequences` gives gap-free boarding sequence numbers. `notifications UNIQUE (event_id, channel, recipient)`
makes delivery idempotent.

Reporting read model: `rpt_flights`, `rpt_bookings` (with `synthetic` flag for generated demo history),
`rpt_checkins`, `rpt_users`, `rpt_activity`, `audit_log`.

## Shared tables (per service)

```sql
outbox_events (id uuid PK, topic, event_type, aggregate_id, payload text, created_at, published_at, attempts, last_error)
  -- partial index on created_at WHERE published_at IS NULL; relay uses FOR UPDATE SKIP LOCKED
processed_events (event_id uuid PK, processed_at)
```

## Conventions

* UUID primary keys generated in the application; `TIMESTAMPTZ` stored in UTC; money as `NUMERIC(12,2)` + ISO currency.
* `@Version` optimistic locking on mutable aggregates (users, flights, bookings, passengers).
* E-mail uniqueness via functional indexes on `lower(email)`.
* No PAN/CVV is ever stored — only card brand, last 4 and the PSP reference. Passport numbers are masked in booking APIs.

## Migrations

Add `V2__<description>.sql` next to `V1__*.sql` in the owning service. Flyway runs on startup
(`baseline-on-migrate`), so deployments are ordered: migrate → validate → serve. Use expand/contract
for breaking changes (add column → backfill → switch reads → drop later).
