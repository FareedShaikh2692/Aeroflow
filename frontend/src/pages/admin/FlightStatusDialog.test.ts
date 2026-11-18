import { describe, expect, it } from "vitest";
import { allowedTransitions } from "./FlightStatusDialog";

describe("flight status transitions (mirrors backend FlightStatus)", () => {
  it("allows operational transitions", () => {
    expect(allowedTransitions("SCHEDULED")).toEqual(["DELAYED", "BOARDING", "CANCELLED"]);
    expect(allowedTransitions("BOARDING")).toContain("DEPARTED");
    expect(allowedTransitions("DEPARTED")).toEqual(["ARRIVED"]);
  });
  it("treats arrived and cancelled as terminal", () => {
    expect(allowedTransitions("ARRIVED")).toEqual([]);
    expect(allowedTransitions("CANCELLED")).toEqual([]);
  });
});
