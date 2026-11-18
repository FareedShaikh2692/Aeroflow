import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, refreshSession, tokenStore } from "./api";
import { STAFF_ROLES, type Role, type TokenResponse, type User } from "./types";

interface AuthState {
  user: User | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string }) => Promise<User>;
  logout: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
  isStaff: boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const unsub = tokenStore.subscribe((s) => setUser(s?.user ?? null));
    refreshSession().finally(() => setReady(true));
    return () => { unsub(); };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const s = await api<TokenResponse>("/api/auth/login", { method: "POST", body: { email, password }, auth: false });
    tokenStore.set(s);
    return s.user;
  }, []);

  const register = useCallback(async (data: Parameters<AuthState["register"]>[0]) => {
    const s = await api<TokenResponse>("/api/auth/register", { method: "POST", body: data, auth: false });
    tokenStore.set(s);
    return s.user;
  }, []);

  const logout = useCallback(async () => {
    const rt = tokenStore.refresh;
    tokenStore.set(null);
    if (rt) await api("/api/auth/logout", { method: "POST", body: { refreshToken: rt }, auth: false }).catch(() => undefined);
  }, []);

  const value = useMemo<AuthState>(() => ({
    user, ready, login, register, logout,
    hasRole: (...roles) => !!user && roles.some((r) => user.roles.includes(r)),
    isStaff: !!user && user.roles.some((r) => STAFF_ROLES.includes(r)),
  }), [user, ready, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

/** Route-level permission map shared by navigation and route guards. */
export const PERMISSIONS = {
  dashboard: STAFF_ROLES,
  operations: ["SUPER_ADMIN", "ADMIN", "OPERATIONS_MANAGER", "FLIGHT_AGENT"] as Role[],
  flights: STAFF_ROLES,
  flightsWrite: ["SUPER_ADMIN", "ADMIN", "OPERATIONS_MANAGER"] as Role[],
  flightsDelete: ["SUPER_ADMIN", "ADMIN"] as Role[],
  passengers: STAFF_ROLES,
  passengersWrite: ["SUPER_ADMIN", "ADMIN", "OPERATIONS_MANAGER", "FLIGHT_AGENT"] as Role[],
  bookings: STAFF_ROLES,
  checkin: ["SUPER_ADMIN", "ADMIN", "CHECKIN_AGENT", "OPERATIONS_MANAGER"] as Role[],
  reports: ["SUPER_ADMIN", "ADMIN", "OPERATIONS_MANAGER"] as Role[],
  notifications: STAFF_ROLES,
  audit: ["SUPER_ADMIN", "ADMIN"] as Role[],
  users: ["SUPER_ADMIN", "ADMIN"] as Role[],
} satisfies Record<string, Role[]>;
