import { describe, expect, it, vi, beforeEach } from "vitest";
import { ApiError, api, errorMessage, qs, refreshSession, tokenStore } from "./api";
import { jsonResponse } from "@/test/utils";

const session = (access: string, refresh: string) => ({ accessToken: access, refreshToken: refresh, expiresIn: 900,
  user: { id: "u1", email: "a@b.co", firstName: "A", lastName: "B", roles: ["PASSENGER"], enabled: true, createdAt: "" } });

describe("api client", () => {
  beforeEach(() => tokenStore.set(null));

  it("sends bearer token and idempotency key", async () => {
    tokenStore.set(session("acc-1", "ref-1") as never);
    const f = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({ ok: true }));
    await api("/api/bookings", { method: "POST", body: { a: 1 }, idempotencyKey: "key-123" });
    const headers = f.mock.calls[0]![1]!.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer acc-1");
    expect(headers.get("Idempotency-Key")).toBe("key-123");
    expect(f.mock.calls[0]![1]!.body).toBe('{"a":1}');
  });

  it("normalises backend errors into ApiError with field errors", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({ code: "VALIDATION_FAILED", message: "Request validation failed",
      fieldErrors: [{ field: "email", message: "must be valid" }], traceId: "t-1" }, 400));
    const err = await api("/api/x").catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 400, code: "VALIDATION_FAILED", traceId: "t-1" });
    expect(err.fieldErrors[0].field).toBe("email");
  });

  it("refreshes once on 401 and retries the original request", async () => {
    tokenStore.set(session("expired", "ref-1") as never);
    const f = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ code: "UNAUTHORIZED" }, 401))
      .mockResolvedValueOnce(jsonResponse(session("fresh", "ref-2")))
      .mockResolvedValueOnce(jsonResponse({ data: 42 }));
    await expect(api("/api/bookings/my")).resolves.toEqual({ data: 42 });
    expect(f).toHaveBeenCalledTimes(3);
    expect((f.mock.calls[2]![1]!.headers as Headers).get("Authorization")).toBe("Bearer fresh");
    expect(tokenStore.refresh).toBe("ref-2");
  });

  it("clears the session when refresh fails", async () => {
    tokenStore.set(session("a", "bad") as never);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({ code: "INVALID_REFRESH_TOKEN" }, 401));
    expect(await refreshSession()).toBeNull();
    expect(tokenStore.access).toBeNull();
    expect(tokenStore.refresh).toBeNull();
  });

  it("de-duplicates concurrent refreshes (single flight)", async () => {
    tokenStore.set(session("a", "ref-1") as never);
    const f = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(session("b", "ref-2")));
    await Promise.all([refreshSession(), refreshSession(), refreshSession()]);
    expect(f).toHaveBeenCalledTimes(1);
  });

  it("maps network failures to a friendly error", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(api("/api/x")).rejects.toMatchObject({ code: "NETWORK_ERROR" });
  });

  it("returns blobs for raw requests and undefined for 204", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(new Response("%PDF", { status: 200 })).mockResolvedValueOnce(new Response(null, { status: 204 }));
    expect(await (await api<Blob>("/pdf", { raw: true })).text()).toBe("%PDF");
    expect(await api("/none")).toBeUndefined();
  });

  it("qs skips empty values; errorMessage handles unknowns", () => {
    expect(qs({ a: 1, b: "", c: undefined, d: "x y" })).toBe("?a=1&d=x+y");
    expect(qs({})).toBe("");
    expect(errorMessage("weird")).toBe("Something went wrong");
  });
});
