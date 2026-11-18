# AeroFlow — Architecture

AeroFlow is an airline operations and passenger platform built as **8 Spring Boot microservices** behind an
API gateway, with a React SPA serving two experiences (staff console and passenger portal).

## System architecture

```mermaid
flowchart LR
  subgraph Clients
    P[Passenger portal] & S[Staff console]
  end
  P & S -->|HTTPS + JWT| GW[API Gateway<br/>Spring Cloud Gateway<br/>CORS · rate limit · correlation id]
  GW --> AUTH[auth-service]
  GW --> FLT[flight-service]
  GW --> PAX[passenger-service]
  GW --> BKG[booking-service]
  GW --> CHK[checkin-service]
  GW --> NTF[notification-service]
  GW --> RPT[reporting-service]
  BKG -- REST /internal --> FLT
  FLT -- REST /internal --> BKG
  CHK -- REST /internal --> BKG
  AUTH & FLT & BKG & CHK & PAX -. outbox .-> K[(Kafka)]
  K -.-> BKG & PAX & NTF & RPT
  GW --- R[(Redis)]
  FLT & BKG --- R
  AUTH --- D1[(aeroflow_auth)]
  FLT --- D2[(aeroflow_flight)]
  PAX --- D3[(aeroflow_passenger)]
  BKG --- D4[(aeroflow_booking)]
  CHK --- D5[(aeroflow_checkin)]
  NTF --- D6[(aeroflow_notification)]
  RPT --- D7[(aeroflow_reporting)]
```

| Service | Owns | Sync dependencies | Publishes | Consumes |
|---|---|---|---|---|
| gateway | routing, CORS, rate limits | all services | — | — |
| auth-service | users, roles, refresh tokens | — | `user.registered`, audit | — |
| flight-service | airports, fleet, flights, status | booking (booking count before delete) | `flight.*`, audit | — |
| passenger-service | profiles, travel-history read model | — | audit | `user.*`, `booking.*`, `checkin.*` |
| booking-service | bookings, seat inventory, payments, idempotency keys | flight (authoritative flight data) | `booking.*` | `flight.status-changed`, `flight.updated` |
| checkin-service | check-ins, boarding passes | booking (retrieve, seat change, mark checked-in) | `checkin.completed` | — |
| notification-service | notification log | — | — | `booking.*`, `checkin.*` |
| reporting-service | reporting read model, audit log | — | — | all topics |

## Communication

```mermaid
sequenceDiagram
  participant S as Service (e.g. booking)
  participant DB as Service DB
  participant R as OutboxRelay (@Scheduled)
  participant K as Kafka
  participant C as Consumer service
  S->>DB: BEGIN; write booking + INSERT outbox_events; COMMIT
  loop every 1s
    R->>DB: SELECT … FOR UPDATE SKIP LOCKED (pending)
    R->>K: send(topic, key=aggregateId, envelope)
    R->>DB: mark published_at
  end
  K->>C: deliver (at-least-once)
  C->>C: EventInbox.firstDelivery(eventId)? (processed_events PK)
  C->>C: apply change in same transaction
```

* **Synchronous (REST)** only where a caller needs an authoritative answer *now* (flight fare/status at booking time,
  booking lookup at check-in). Internal calls hit `/internal/**`, require a short-lived service JWT (`ROLE_SERVICE`)
  and are blocked at the gateway. Every call has connect/read timeouts, exponential-backoff retry and a circuit
  breaker (Resilience4j); failures surface as `503 UPSTREAM_UNAVAILABLE`.
* **Asynchronous (Kafka)** for everything else. Events use a common envelope
  `{eventId, type, source, aggregateId, occurredAt, payload}` keyed by aggregate id (per-aggregate ordering).
* **Transactional outbox** guarantees no event is lost or published for a rolled-back transaction.
* **Idempotent consumers**: each service records processed event ids; redeliveries are no-ops.
  Poison messages are retried with backoff then parked on `<topic>.DLT`.

## Booking sequence (seat concurrency + idempotency)

```mermaid
sequenceDiagram
  actor U as Passenger
  participant GW as Gateway
  participant B as booking-service
  participant F as flight-service
  participant DB as booking DB
  participant PSP as Mock PSP
  U->>GW: POST /api/bookings/guest (Idempotency-Key)
  GW->>B: forward (rate limited)
  B->>DB: idempotency key seen? -> replay original booking
  B->>F: GET /internal/flights/{id} (cached, retried)
  B->>DB: INSERT inventory ON CONFLICT DO NOTHING
  B->>DB: SELECT inventory FOR UPDATE   (serialises this flight)
  B->>DB: re-check occupied seats / capacity
  B->>PSP: charge (decline -> 402, nothing persisted)
  B->>DB: insert booking, passengers, seat_assignments (partial UNIQUE index), payment, idempotency key, outbox
  B-->>U: 201 Booking (PNR)
  Note over B,DB: COMMIT -> relay publishes booking.confirmed -> notifications, passenger history, reporting
```

Three layers stop double booking: the per-flight row lock, the in-lock availability re-check and the partial unique
index `ux_seat_active (flight_id, seat_number) WHERE released = false`. Verified by a 10-thread race test in
`scripts/smoke_test.py` (exactly one 201, nine 409s).

## Check-in sequence

```mermaid
sequenceDiagram
  actor U as Passenger / agent
  participant C as checkin-service
  participant B as booking-service
  U->>C: POST /api/checkin/retrieve {pnr, lastName}
  C->>B: POST /internal/bookings/retrieve (verifies surname)
  C-->>U: booking + eligibility window (opens 48h, closes 60m) + existing passes
  U->>C: PUT /api/checkin/seat (optional)
  C->>B: PUT /internal/bookings/{pnr}/passengers/{id}/seat (locked seat swap)
  U->>C: POST /api/checkin/complete {passengerIds, acceptDeclaration}
  C->>B: POST /internal/bookings/{pnr}/check-in (idempotent)
  C->>C: allocate boarding sequence (atomic upsert) + persist boarding passes + outbox checkin.completed
  U->>C: POST /api/checkin/boarding-pass -> PDF with IATA BCBP QR code
```

## Cross-cutting concerns

| Concern | Implementation |
|---|---|
| Errors | `GlobalExceptionHandler` → single `ApiError {timestamp,status,code,message,path,traceId,fieldErrors}` |
| Validation | Bean Validation on every DTO + domain invariants in services/entities |
| AuthN/Z | Stateless JWT (HS256), rotating refresh tokens with reuse detection, `@PreAuthorize` RBAC per endpoint |
| Concurrency | Pessimistic row lock per flight, partial unique index, `@Version` optimistic locking (`If-Match` on flight edits) |
| Idempotency | `Idempotency-Key` header on booking creation (request hash checked), idempotent check-in, inbox de-dup |
| Caching | Redis cache-aside (airports 6h, flight 5m, search 60s, layout 10m) with evict-on-write; cache errors degrade to misses |
| Rate limiting | Redis token bucket at the gateway (auth: 5 rps / burst 20, bookings: 20 rps / burst 60 per IP) + account lockout |
| Observability | Actuator health/readiness/liveness, Micrometer → Prometheus, Grafana dashboard, JSON logs with `traceId` |
| Time | All instants stored UTC (`TIMESTAMPTZ`); search uses the origin airport's IANA timezone |

## Architecture decision records (summary)

1. **Database per service, one Postgres instance** — strong ownership without operational overhead; a schema-per-service
   mode exists for single-database managed plans.
2. **Outbox over dual writes** — avoids lost/phantom events; relay uses `SKIP LOCKED` so it scales horizontally.
3. **Pessimistic lock for seats** — seat inventory is a hot, contended resource where optimistic retries thrash;
   holding one row lock for a short transaction is simple and provably correct, backed by a unique index.
4. **HS256 shared secret** — simplest correct option for a single trust domain; upgrade path is RS256 + JWKS from auth-service.
5. **Read models for history and reporting** — passenger-service and reporting-service build their own projections from
   events instead of querying other services' tables (CQRS-lite).
6. **Gateway terminates CORS and rate limiting** — services stay simple and never see browser preflights.
7. **Mock adapters behind ports** — `PaymentGateway`, `EmailSender`, `SmsSender` interfaces make real providers a drop-in change.
