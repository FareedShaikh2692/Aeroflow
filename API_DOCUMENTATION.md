# AeroFlow — API Documentation

All traffic goes through the gateway (`http://localhost:8080` locally). Each service also serves live
**OpenAPI 3** docs and Swagger UI directly:

| Service | Swagger UI (local) | Spec in repo |
|---|---|---|
| auth | http://localhost:8081/swagger-ui.html | [docs/openapi/auth-service.json](docs/openapi/auth-service.json) |
| flight | http://localhost:8082/swagger-ui.html | [docs/openapi/flight-service.json](docs/openapi/flight-service.json) |
| passenger | http://localhost:8083/swagger-ui.html | [docs/openapi/passenger-service.json](docs/openapi/passenger-service.json) |
| booking | http://localhost:8084/swagger-ui.html | [docs/openapi/booking-service.json](docs/openapi/booking-service.json) |
| check-in | http://localhost:8085/swagger-ui.html | [docs/openapi/checkin-service.json](docs/openapi/checkin-service.json) |
| notification | http://localhost:8086/swagger-ui.html | [docs/openapi/notification-service.json](docs/openapi/notification-service.json) |
| reporting | http://localhost:8087/swagger-ui.html | [docs/openapi/reporting-service.json](docs/openapi/reporting-service.json) |

The JSON specs above were exported from the running services (`/v3/api-docs`).

## Conventions

* **Auth**: `Authorization: Bearer <accessToken>` (15 min). Renew with `POST /api/auth/refresh` (rotating refresh token, 7 days).
* **Idempotency**: `POST /api/bookings` and `/api/bookings/guest` require `Idempotency-Key: <8-80 chars [A-Za-z0-9_-]>`.
  Replays return the original booking; reusing a key with a different body returns `422 IDEMPOTENCY_KEY_REUSED`.
* **Optimistic concurrency**: `PUT /api/flights/{id}` accepts `If-Match: "<version>"` → `409 STALE_VERSION` if stale.
* **Pagination**: `?page=0&size=20` → `{content, page, size, totalElements, totalPages}` (max size 100).
* **Tracing**: every response carries `X-Correlation-Id` (propagated across services and into logs).
* **Times** are ISO-8601 UTC instants; dates are `YYYY-MM-DD`.

## Error contract

```json
{
  "timestamp": "2026-11-18T15:20:11.042Z",
  "status": 409,
  "error": "Conflict",
  "code": "SEAT_UNAVAILABLE",
  "message": "Seat(s) no longer available: 9C",
  "path": "/api/bookings/guest",
  "traceId": "1f0c7a8e-…",
  "fieldErrors": [{ "field": "contactEmail", "message": "must be a well-formed email address" }]
}
```

Common codes: `VALIDATION_FAILED` (400), `UNAUTHORIZED` (401), `INVALID_CREDENTIALS` (401), `PAYMENT_DECLINED` (402),
`FORBIDDEN` (403), `NOT_FOUND` (404), `ACCOUNT_LOCKED` (423), `SEAT_UNAVAILABLE` / `INSUFFICIENT_SEATS` /
`FLIGHT_NOT_BOOKABLE` / `CHECKIN_NOT_OPEN` / `INVALID_STATUS_TRANSITION` / `FLIGHT_HAS_BOOKINGS` (409),
`IDEMPOTENCY_KEY_REUSED` (422), `429` (rate limited), `UPSTREAM_UNAVAILABLE` (503).

## Roles

| Role | Access |
|---|---|
| SUPER_ADMIN | everything, incl. granting SUPER_ADMIN |
| ADMIN | everything except SUPER_ADMIN accounts; user management, audit log, deletes |
| OPERATIONS_MANAGER | dashboard, flights (create/edit/status), reports, bookings, passengers |
| FLIGHT_AGENT | flights (read), bookings, passengers (create/edit) |
| CHECKIN_AGENT | check-in desk, bookings (read), passengers (read) |
| PASSENGER | own bookings, profile, notifications |

## Example: book and check in

```bash
GW=http://localhost:8080
FLIGHT=$(curl -s "$GW/api/flights/search?origin=DXB&destination=LHR&date=$(date -v+1d +%F)" | jq -r '.[0].id')
curl -s "$GW/api/seat-maps/$FLIGHT" | jq '{available, occupied: (.occupied|length)}'
curl -s -X POST "$GW/api/bookings/guest" -H 'Content-Type: application/json' -H "Idempotency-Key: demo-$(uuidgen)" -d '{
  "flightId":"'$FLIGHT'","contactEmail":"sarah@example.com",
  "passengers":[{"firstName":"Sarah","lastName":"Ahmed","cabin":"ECONOMY","seatNumber":"12A"}],
  "payment":{"cardNumber":"4242 4242 4242 4242","expiry":"12/30","cvv":"123","cardHolder":"Sarah Ahmed"}}' | jq '{pnr,status,totalAmount}'
curl -s -X POST "$GW/api/checkin/retrieve" -H 'Content-Type: application/json' -d '{"pnr":"<PNR>","lastName":"Ahmed"}'
```

Test cards (mock PSP): `4242 4242 4242 4242` approved · `4000 0000 0000 0002` declined · any non-Luhn number rejected.

## Event catalogue (Kafka)

| Topic | Event types | Producer |
|---|---|---|
| `aeroflow.user.events` | `user.registered` | auth |
| `aeroflow.flight.events` | `flight.created`, `flight.updated`, `flight.status-changed`, `flight.deleted` | flight |
| `aeroflow.booking.events` | `booking.confirmed`, `booking.cancelled`, `booking.flight-notice`, `booking.checkin-reminder` | booking |
| `aeroflow.checkin.events` | `checkin.completed` | check-in |
| `aeroflow.audit.events` | `audit.recorded` | all write services |

## Public endpoints (generated from the OpenAPI specs; `/internal/**` omitted)

| Service | Method | Path | Summary |
|---|---|---|---|
| auth-service | `GET` | `/api/users/{id}` | get |
| auth-service | `PUT` | `/api/users/{id}` | update |
| auth-service | `GET` | `/api/users` | list |
| auth-service | `POST` | `/api/users` | create |
| auth-service | `POST` | `/api/users/{id}/reset-password` | resetPassword |
| auth-service | `POST` | `/api/auth/register` | Self-register a passenger account |
| auth-service | `POST` | `/api/auth/refresh` | Rotate refresh token and obtain a new access token |
| auth-service | `POST` | `/api/auth/logout` | Revoke the refresh token family |
| auth-service | `POST` | `/api/auth/login` | Authenticate with e-mail and password |
| auth-service | `POST` | `/api/auth/change-password` | changePassword |
| auth-service | `GET` | `/api/auth/me` | me |
| booking-service | `PUT` | `/api/bookings/{pnr}/passengers/{passengerId}/seat` | staffSeatChange |
| booking-service | `GET` | `/api/bookings` | search |
| booking-service | `POST` | `/api/bookings` | Create a booking as an authenticated passenger or agent. Requires Idempotency-Key. |
| booking-service | `POST` | `/api/bookings/{pnr}/cancel` | cancel |
| booking-service | `POST` | `/api/bookings/retrieve` | Retrieve a booking with PNR + passenger last name (public) |
| booking-service | `POST` | `/api/bookings/quote` | quote |
| booking-service | `POST` | `/api/bookings/guest` | Create a booking (guest or signed-in). Requires Idempotency-Key. |
| booking-service | `POST` | `/api/bookings/guest/{pnr}/cancel` | Cancel as a guest (last name verification) |
| booking-service | `GET` | `/api/seat-maps/{flightId}` | Seat map with occupied seats (public) |
| booking-service | `GET` | `/api/bookings/{pnr}` | get |
| booking-service | `GET` | `/api/bookings/my` | mine |
| booking-service | `GET` | `/api/bookings/flight/{flightId}/manifest` | manifest |
| checkin-service | `PUT` | `/api/checkin/seat` | seat |
| checkin-service | `POST` | `/api/checkin/retrieve` | Retrieve booking for check-in (PNR + last name) |
| checkin-service | `POST` | `/api/checkin/complete` | Check in selected passengers and issue boarding passes |
| checkin-service | `POST` | `/api/checkin/boarding-pass` | Download a boarding pass as PDF |
| checkin-service | `GET` | `/api/checkin` | list |
| flight-service | `GET` | `/api/flights/{id}` | get |
| flight-service | `PUT` | `/api/flights/{id}` | Update a flight; pass If-Match with the version for optimistic concurrency |
| flight-service | `DELETE` | `/api/flights/{id}` | delete |
| flight-service | `GET` | `/api/flights` | list |
| flight-service | `POST` | `/api/flights` | create |
| flight-service | `GET` | `/api/aircraft` | fleet |
| flight-service | `POST` | `/api/aircraft` | create_1 |
| flight-service | `PATCH` | `/api/flights/{id}/status` | Change operational status: delay, board, depart, arrive or cancel |
| flight-service | `PATCH` | `/api/aircraft/{id}/status` | status |
| flight-service | `GET` | `/api/flights/stats` | stats |
| flight-service | `GET` | `/api/flights/search` | Public flight search by route and local departure date |
| flight-service | `GET` | `/api/flights/public/{id}` | Public flight details (used by booking and check-in flows) |
| flight-service | `GET` | `/api/airports` | airports |
| notification-service | `GET` | `/api/notifications` | list |
| notification-service | `GET` | `/api/notifications/me` | mine |
| passenger-service | `GET` | `/api/passengers/{id}` | get |
| passenger-service | `PUT` | `/api/passengers/{id}` | update |
| passenger-service | `DELETE` | `/api/passengers/{id}` | delete |
| passenger-service | `GET` | `/api/passengers/me` | me |
| passenger-service | `PUT` | `/api/passengers/me` | updateMe |
| passenger-service | `GET` | `/api/passengers` | list |
| passenger-service | `POST` | `/api/passengers` | create |
| reporting-service | `GET` | `/api/reports/{type}` | flights | bookings | passengers | revenue report, JSON or CSV (format=csv) |
| reporting-service | `GET` | `/api/reports/dashboard` | Executive dashboard KPIs, charts, activity and alerts |
| reporting-service | `GET` | `/api/audit` | audit |
| reporting-service | `GET` | `/api/audit/actions` | auditActions |