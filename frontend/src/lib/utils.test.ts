import { describe, expect, it } from "vitest";
import { cn, formatDuration, formatMoney, formatTime, initials, newIdempotencyKey } from "./utils";

describe("utils", () => {
  it("merges tailwind classes", () => expect(cn("px-2", false && "x", "px-4")).toBe("px-4"));
  it("formats durations", () => expect(formatDuration(470)).toBe("7h 50m"));
  it("formats money without decimals", () => expect(formatMoney(2850.4, "AED")).toMatch(/2,850/));
  it("formats time in a given zone", () => expect(formatTime("2030-01-01T04:00:00Z", "Asia/Dubai")).toBe("08:00"));
  it("builds initials", () => expect(initials("sarah ahmed")).toBe("SA"));
  it("creates unique idempotency keys", () => {
    const a = newIdempotencyKey();
    expect(a).toMatch(/^web-/);
    expect(a).not.toBe(newIdempotencyKey());
  });
});
