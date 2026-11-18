import { describe, expect, it } from "vitest";
import { clean, flightSchema, loginSchema, luhn, passengersFormSchema, paymentSchema, pnrSchema, registerSchema, searchSchema } from "./schemas";

describe("luhn", () => {
  it.each([["4242 4242 4242 4242", true], ["4000 0000 0000 0002", true], ["4242 4242 4242 4241", false], ["1234", false], ["", false]])(
    "%s -> %s", (n, ok) => expect(luhn(n)).toBe(ok));
});

describe("loginSchema", () => {
  it("requires a valid e-mail and a password", () => {
    const r = loginSchema.safeParse({ email: "nope", password: "" });
    expect(r.success).toBe(false);
    expect(r.error?.flatten().fieldErrors).toMatchObject({ email: ["Enter a valid e-mail address"], password: ["Password is required"] });
  });
});

describe("registerSchema", () => {
  const base = { firstName: "Sarah", lastName: "Ahmed", email: "s@x.com", phone: "", password: "Str0ng!Passw", confirm: "Str0ng!Passw" };
  it("accepts a strong matching password", () => expect(registerSchema.safeParse(base).success).toBe(true));
  it("rejects weak passwords", () => expect(registerSchema.safeParse({ ...base, password: "weakpass", confirm: "weakpass" }).success).toBe(false));
  it("rejects mismatched confirmation", () => {
    const r = registerSchema.safeParse({ ...base, confirm: "Other0!Passw" });
    expect(r.error?.flatten().fieldErrors.confirm).toEqual(["Passwords do not match"]);
  });
  it("rejects digits in names", () => expect(registerSchema.safeParse({ ...base, firstName: "S4rah" }).success).toBe(false));
});

describe("searchSchema", () => {
  it("rejects identical origin and destination", () => {
    const r = searchSchema.safeParse({ origin: "DXB", destination: "DXB", date: "2030-01-01", passengers: 1, cabin: "ECONOMY" });
    expect(r.error?.flatten().fieldErrors.destination).toBeDefined();
  });
  it("coerces passenger count and caps it at 9", () => {
    expect(searchSchema.safeParse({ origin: "DXB", destination: "LHR", date: "2030-01-01", passengers: "2", cabin: "ECONOMY" }).success).toBe(true);
    expect(searchSchema.safeParse({ origin: "DXB", destination: "LHR", date: "2030-01-01", passengers: 10, cabin: "ECONOMY" }).success).toBe(false);
  });
});

describe("paymentSchema", () => {
  const ok = { cardHolder: "Sarah Ahmed", cardNumber: "4242424242424242", expiry: "12/39", cvv: "123", acceptTerms: true };
  it("accepts a valid card", () => expect(paymentSchema.safeParse(ok).success).toBe(true));
  it("rejects expired cards", () => expect(paymentSchema.safeParse({ ...ok, expiry: "01/20" }).error?.flatten().fieldErrors.expiry).toEqual(["Card has expired"]));
  it("rejects bad expiry format and CVV", () => {
    const e = paymentSchema.safeParse({ ...ok, expiry: "1/30", cvv: "12" }).error?.flatten().fieldErrors;
    expect(e?.expiry).toBeDefined();
    expect(e?.cvv).toBeDefined();
  });
  it("requires accepting the fare rules", () => expect(paymentSchema.safeParse({ ...ok, acceptTerms: false }).success).toBe(false));
});

describe("passengersFormSchema", () => {
  it("upper-cases nationality and passport", () => {
    const r = passengersFormSchema.parse({ travellers: [{ firstName: "A", lastName: "B", nationality: "ae", passportNumber: "p12345" }], contactEmail: "a@b.co" });
    expect(r.travellers[0]).toMatchObject({ nationality: "AE", passportNumber: "P12345" });
  });
  it("rejects a future date of birth", () => {
    expect(passengersFormSchema.safeParse({ travellers: [{ firstName: "A", lastName: "B", dateOfBirth: "2999-01-01" }], contactEmail: "a@b.co" }).success).toBe(false);
  });
});

describe("pnrSchema", () => {
  it("normalises to upper case", () => expect(pnrSchema.parse({ pnr: "k7q2mx", lastName: "Ahmed" }).pnr).toBe("K7Q2MX"));
  it("rejects wrong length", () => expect(pnrSchema.safeParse({ pnr: "ABC", lastName: "x" }).success).toBe(false));
});

describe("flightSchema", () => {
  const base = { flightNumber: "af201", originCode: "DXB", destinationCode: "LHR", aircraftId: "3f5c8a2e-1b4d-4c6e-9f00-112233445566",
    departureTime: "2030-01-01T08:00", arrivalTime: "2030-01-01T15:00", economyFare: 1000, businessFare: 3000 };
  it("accepts a valid flight and upper-cases the number", () => expect(flightSchema.parse(base).flightNumber).toBe("AF201"));
  it("rejects arrival before departure", () => expect(flightSchema.safeParse({ ...base, arrivalTime: "2030-01-01T07:00" }).success).toBe(false));
  it("rejects business fare below economy", () => expect(flightSchema.safeParse({ ...base, businessFare: 500 }).success).toBe(false));
});

describe("clean", () => {
  it("turns empty strings into undefined", () => expect(clean({ a: "", b: "x", c: 0 })).toEqual({ a: undefined, b: "x", c: 0 }));
});
