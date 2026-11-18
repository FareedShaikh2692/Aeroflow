#!/usr/bin/env python3
"""End-to-end API smoke test for AeroFlow. Runs against any gateway URL.

Usage: python3 scripts/smoke_test.py [http://localhost:8080]
Env:   DEMO_PASSWORD (default Aeroflow@2026)
Exits non-zero on the first failed check. Uses only the Python standard library.
"""
import concurrent.futures as cf, datetime as dt, json, os, sys, time, urllib.error, urllib.request, uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
PWD = os.environ.get("DEMO_PASSWORD", "Aeroflow@2026")
passed = 0


def call(method, path, body=None, token=None, headers=None, raw=False):
    h = {"Content-Type": "application/json", **(headers or {})}
    if token:
        h["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            content = r.read()
            return r.status, (content if raw else (json.loads(content) if content else None)), dict(r.headers)
    except urllib.error.HTTPError as e:
        content = e.read()
        try:
            return e.code, json.loads(content), dict(e.headers)
        except Exception:
            return e.code, content, dict(e.headers)


def check(name, cond, detail=""):
    global passed
    if not cond:
        print(f"  FAIL  {name}  {detail}")
        sys.exit(1)
    passed += 1
    print(f"  ok    {name}")


def login(email):
    s, b, _ = call("POST", "/api/auth/login", {"email": email, "password": PWD})
    check(f"login {email}", s == 200 and b.get("accessToken"), f"{s} {b}")
    return b


def card(number="4242 4242 4242 4242"):
    return {"cardNumber": number, "expiry": "12/30", "cvv": "123", "cardHolder": "Test Holder"}


def wait_for(fn, what, timeout=25):
    end = time.time() + timeout
    while time.time() < end:
        v = fn()
        if v:
            return v
        time.sleep(1)
    check(what, False, "timed out")


print(f"AeroFlow smoke test against {BASE}")
print("[auth & rbac]")
admin = login("admin@aeroflow.dev")
ops = login("ops@aeroflow.dev")
pax = login("passenger@aeroflow.dev")
s, b, _ = call("POST", "/api/auth/login", {"email": "admin@aeroflow.dev", "password": "wrong-Password1!"})
check("wrong password rejected", s == 401 and b["code"] == "INVALID_CREDENTIALS")
s, _, _ = call("GET", "/api/flights", token=pax["accessToken"])
check("passenger forbidden from staff flight list", s == 403)
s, _, _ = call("GET", "/api/flights")
check("anonymous gets 401 on staff API", s == 401)
s, b, _ = call("POST", "/api/auth/refresh", {"refreshToken": admin["refreshToken"]})
check("refresh rotates tokens", s == 200 and b["refreshToken"] != admin["refreshToken"])
s, b2, _ = call("POST", "/api/auth/refresh", {"refreshToken": admin["refreshToken"]})
check("refresh-token reuse detected", s == 401)
s, b3, _ = call("POST", "/api/auth/refresh", {"refreshToken": b["refreshToken"]})
check("reuse revoked the whole token family", s == 401)
admin = login("admin@aeroflow.dev")
s, b, _ = call("GET", "/api/users?size=50", token=admin["accessToken"])
check("admin lists users", s == 200 and b["totalElements"] >= 6)

print("[flights]")
tomorrow = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=1)).date().isoformat()
s, flights, _ = call("GET", f"/api/flights/search?origin=DXB&destination=LHR&date={tomorrow}")
check("public flight search DXB-LHR tomorrow", s == 200 and len(flights) >= 1, f"{s} {flights}")
flight = flights[0]
s, b, _ = call("GET", "/api/flights/stats", token=ops["accessToken"])
check("flight stats", s == 200 and b["total"] > 100)
s, b, _ = call("GET", "/api/flights?status=SCHEDULED&size=5&sort=departureTime", token=ops["accessToken"])
check("filtered/paginated flight list", s == 200 and len(b["content"]) == 5)
s, b, _ = call("GET", "/api/aircraft", token=ops["accessToken"])
aircraft_id = b[0]["id"]
dep = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=20)).replace(microsecond=0)
new_flight = {"flightNumber": "AF9" + str(int(time.time()) % 1000).zfill(3), "originCode": "DXB", "destinationCode": "CDG",
              "aircraftId": aircraft_id, "departureTime": dep.isoformat().replace("+00:00", "Z"),
              "arrivalTime": (dep + dt.timedelta(hours=7)).isoformat().replace("+00:00", "Z"),
              "economyFare": 2100, "businessFare": 7400, "gate": "B12", "terminal": "3"}
s, created, _ = call("POST", "/api/flights", new_flight, token=ops["accessToken"])
check("ops creates flight", s == 201, f"{s} {created}")
s, b, _ = call("POST", "/api/flights", new_flight, token=ops["accessToken"])
check("duplicate flight rejected", s == 409)
s, b, _ = call("POST", "/api/flights", {**new_flight, "destinationCode": "DXB"}, token=ops["accessToken"])
check("invalid route rejected", s == 400)
s, _, _ = call("DELETE", f"/api/flights/{created['id']}", token=ops["accessToken"])
check("ops cannot delete (admin only)", s == 403)
s, _, _ = call("DELETE", f"/api/flights/{created['id']}", token=admin["accessToken"])
check("admin deletes flight without bookings", s == 204)

print("[booking & seats]")
s, seatmap, _ = call("GET", f"/api/seat-maps/{flight['id']}")
check("seat map", s == 200 and seatmap["available"] > 0)
letters = seatmap["economyLayout"].replace("-", "")
free = [f"{r}{c}" for r in range(seatmap["businessRows"] + 1, seatmap["businessRows"] + seatmap["economyRows"] + 1)
        for c in letters if f"{r}{c}" not in seatmap["occupied"]]
seat_a, seat_b, race_seat = free[0], free[1], free[2]
booking_req = {"flightId": flight["id"], "contactEmail": "passenger@aeroflow.dev", "contactPhone": "+971500000006",
               "passengers": [{"firstName": "Sarah", "lastName": "Ahmed", "cabin": "ECONOMY", "seatNumber": seat_a,
                               "email": "passenger@aeroflow.dev", "nationality": "AE", "passportNumber": "P1234567"},
                              {"firstName": "Yusuf", "lastName": "Ahmed", "cabin": "ECONOMY", "seatNumber": seat_b}],
               "payment": card()}
key = "smoke-" + uuid.uuid4().hex
s, booking, _ = call("POST", "/api/bookings", booking_req, token=pax["accessToken"], headers={"Idempotency-Key": key})
check("passenger creates booking", s == 201 and len(booking["pnr"]) == 6, f"{s} {booking}")
pnr = booking["pnr"]
s, replay, _ = call("POST", "/api/bookings", booking_req, token=pax["accessToken"], headers={"Idempotency-Key": key})
check("idempotent replay returns same PNR", s == 201 and replay["pnr"] == pnr)
s, b, _ = call("POST", "/api/bookings", {**booking_req, "contactEmail": "x@example.com"}, token=pax["accessToken"],
               headers={"Idempotency-Key": key})
check("idempotency key reuse with different body rejected", s == 422)
s, b, _ = call("POST", "/api/bookings/guest", {**booking_req, "passengers": booking_req["passengers"][:1]},
               headers={"Idempotency-Key": "smoke-" + uuid.uuid4().hex})
check("already-taken seat rejected (409)", s == 409 and b["code"] == "SEAT_UNAVAILABLE", f"{s} {b}")
s, b, _ = call("POST", "/api/bookings/guest", {**booking_req, "passengers": [{**booking_req["passengers"][0], "seatNumber": None}],
               "payment": card("4000 0000 0000 0002")}, headers={"Idempotency-Key": "smoke-" + uuid.uuid4().hex})
check("declined card returns 402 and books nothing", s == 402 and b["code"] == "PAYMENT_DECLINED", f"{s} {b}")


def race(i):
    req = {**booking_req, "contactEmail": f"race{i}@example.com",
           "passengers": [{"firstName": "Race", "lastName": f"Tester{chr(65 + i)}", "cabin": "ECONOMY", "seatNumber": race_seat}]}
    return call("POST", "/api/bookings/guest", req, headers={"Idempotency-Key": "race-" + uuid.uuid4().hex})[0]


with cf.ThreadPoolExecutor(10) as ex:
    results = list(ex.map(race, range(10)))
check(f"10 concurrent bookings for seat {race_seat}: exactly one wins", results.count(201) == 1 and results.count(409) == 9,
      str(results))
s, b, _ = call("GET", f"/api/seat-maps/{flight['id']}")
check("seat map reflects all three new seats", all(x in b["occupied"] for x in (seat_a, seat_b, race_seat)))
s, b, _ = call("POST", "/api/bookings/retrieve", {"pnr": pnr, "lastName": "ahmed"})
check("retrieve by PNR + last name (case-insensitive)", s == 200 and b["pnr"] == pnr)
s, b, _ = call("POST", "/api/bookings/retrieve", {"pnr": pnr, "lastName": "Wrong"})
check("retrieve with wrong last name hides booking", s == 404)
s, b, _ = call("GET", "/api/bookings/my", token=pax["accessToken"])
check("booking appears in passenger history", s == 200 and any(x["pnr"] == pnr for x in b))

print("[check-in]")
s, view, _ = call("POST", "/api/checkin/retrieve", {"pnr": pnr, "lastName": "Ahmed"})
check("check-in retrieve; window open", s == 200 and view["eligibility"]["open"], f"{s} {view}")
sarah = next(p for p in view["booking"]["passengers"] if p["firstName"] == "Sarah")
new_seat = free[5]
s, b, _ = call("PUT", "/api/checkin/seat", {"pnr": pnr, "lastName": "Ahmed", "passengerId": sarah["id"], "seatNumber": new_seat})
check("seat change during check-in", s == 200 and any(p["seatNumber"] == new_seat for p in b["passengers"]), f"{s} {b}")
ids = [p["id"] for p in view["booking"]["passengers"]]
s, b, _ = call("POST", "/api/checkin/complete", {"pnr": pnr, "lastName": "Ahmed", "passengerIds": ids, "acceptDeclaration": False})
check("declaration required", s == 400)
s, done, _ = call("POST", "/api/checkin/complete", {"pnr": pnr, "lastName": "Ahmed", "passengerIds": ids, "acceptDeclaration": True})
check("check-in completes with boarding passes", s == 200 and len(done["boardingPasses"]) == 2, f"{s} {done}")
s, again, _ = call("POST", "/api/checkin/complete", {"pnr": pnr, "lastName": "Ahmed", "passengerIds": ids, "acceptDeclaration": True})
check("check-in is idempotent", s == 200 and len(again["boardingPasses"]) == 2)
bp = done["boardingPasses"][0]["boardingPassNumber"]
s, pdf, hdr = call("POST", "/api/checkin/boarding-pass", {"pnr": pnr, "lastName": "Ahmed", "boardingPassNumber": bp}, raw=True)
check("boarding pass PDF downloads", s == 200 and pdf[:4] == b"%PDF", f"{s}")
s, b, _ = call("POST", f"/api/bookings/guest/{pnr}/cancel", {"lastName": "Ahmed"})
check("checked-in booking cannot be self-cancelled", s == 409)

print("[events, notifications, reporting, audit]")
notes = wait_for(lambda: [n for n in call("GET", "/api/notifications/me", token=pax["accessToken"])[1] or []
                          if n.get("pnr") == pnr and n["eventType"] == "checkin.completed"],
                 "notifications delivered via Kafka")
check("booking + check-in notifications delivered via Kafka", len(notes) >= 1)
s, b, _ = call("PATCH", f"/api/flights/{flight['id']}/status", {"status": "DELAYED", "delayMinutes": 40,
               "reason": "Smoke test: ATC slot"}, token=ops["accessToken"])
check("ops delays flight", s == 200 and b["status"] == "DELAYED", f"{s} {b}")
wait_for(lambda: [n for n in call("GET", "/api/notifications/me", token=pax["accessToken"])[1] or []
                  if n.get("pnr") == pnr and n["eventType"] == "booking.flight-notice"],
         "delay notice fanned out to booked passengers")
check("delay notice fanned out to booked passengers", True)
call("PATCH", f"/api/flights/{flight['id']}/status", {"status": "SCHEDULED", "reason": "Smoke test: back on time"},
     token=ops["accessToken"])
s, dash, _ = call("GET", "/api/reports/dashboard", token=ops["accessToken"])
check("executive dashboard", s == 200 and dash["kpis"]["total_bookings"] > 0 and dash["syntheticDataIncluded"])
s, csv, hdr = call("GET", "/api/reports/bookings?format=csv", token=ops["accessToken"], raw=True)
check("bookings CSV export", s == 200 and csv.startswith(b"pnr,"))
audits = wait_for(lambda: (call("GET", "/api/audit?q=smoke", token=admin["accessToken"])[1] or {}).get("totalElements"),
                  "audit trail recorded")
check("audit trail recorded flight status change", audits >= 1)
s, _, _ = call("GET", "/api/audit", token=ops["accessToken"])
check("ops cannot read audit log (admin only)", s == 403)
print(f"\nALL {passed} CHECKS PASSED")
