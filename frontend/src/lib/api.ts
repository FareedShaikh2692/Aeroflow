import type { TokenResponse } from "./types";

/**
 * Fetch wrapper for the AeroFlow gateway.
 * - Access token lives in memory only; the rotating refresh token is persisted so sessions survive reloads.
 * - A 401 triggers one single-flight refresh, then the original request is retried.
 * - Errors are normalised into ApiError using the backend's error contract.
 */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
const REFRESH_KEY = "aeroflow.refreshToken";

export interface FieldError { field: string; message: string }

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fieldErrors: FieldError[] = [],
              public traceId?: string) {
    super(message);
  }
}

type Listener = (session: TokenResponse | null) => void;
let accessToken: string | null = null;
let refreshInFlight: Promise<TokenResponse | null> | null = null;
const listeners = new Set<Listener>();

export const tokenStore = {
  get access() { return accessToken; },
  get refresh() {
    try { return localStorage.getItem(REFRESH_KEY); } catch { return null; }
  },
  set(session: TokenResponse | null) {
    accessToken = session?.accessToken ?? null;
    try {
      if (session) localStorage.setItem(REFRESH_KEY, session.refreshToken);
      else localStorage.removeItem(REFRESH_KEY);
    } catch { /* storage unavailable */ }
    listeners.forEach((l) => l(session));
  },
  subscribe(l: Listener) {
    listeners.add(l);
    return () => listeners.delete(l);
  },
};

export async function refreshSession(): Promise<TokenResponse | null> {
  const rt = tokenStore.refresh;
  if (!rt) return null;
  refreshInFlight ??= (async () => {
    try {
      const res = await fetch(`${API_BASE}/api/auth/refresh`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: rt }),
      });
      if (!res.ok) {
        tokenStore.set(null);
        return null;
      }
      const session = (await res.json()) as TokenResponse;
      tokenStore.set(session);
      return session;
    } catch {
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  auth?: boolean;
  raw?: boolean;
  idempotencyKey?: string;
}

export async function api<T = unknown>(path: string, opts: RequestOptions = {}, retried = false): Promise<T> {
  const { body, auth = true, raw, idempotencyKey, headers, ...init } = opts;
  const h = new Headers(headers);
  if (body !== undefined) h.set("Content-Type", "application/json");
  if (idempotencyKey) h.set("Idempotency-Key", idempotencyKey);
  if (auth && accessToken) h.set("Authorization", `Bearer ${accessToken}`);

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { ...init, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "Unable to reach AeroFlow services. Check your connection and try again.");
  }

  if (res.status === 401 && auth && !retried && tokenStore.refresh && !path.startsWith("/api/auth/")) {
    const refreshed = await refreshSession();
    if (refreshed) return api<T>(path, opts, true);
  }
  if (!res.ok) {
    let payload: { code?: string; message?: string; fieldErrors?: FieldError[]; traceId?: string } = {};
    try { payload = await res.json(); } catch { /* non-JSON error */ }
    throw new ApiError(res.status, payload.code ?? `HTTP_${res.status}`,
      payload.message ?? (res.status === 429 ? "Too many requests - please slow down." : res.statusText || "Request failed"),
      payload.fieldErrors ?? [], payload.traceId);
  }
  if (raw) return (await res.blob()) as T;
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function qs(params: Record<string, string | number | boolean | null | undefined>) {
  const s = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") s.set(k, String(v));
  });
  const out = s.toString();
  return out ? `?${out}` : "";
}

export function errorMessage(e: unknown) {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return "Something went wrong";
}
