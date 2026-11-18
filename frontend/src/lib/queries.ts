import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { Aircraft, Airport } from "./types";

export function useAirports() {
  return useQuery({ queryKey: ["airports"], queryFn: () => api<Airport[]>("/api/airports", { auth: false }), staleTime: 3_600_000 });
}

export function useAircraft() {
  return useQuery({ queryKey: ["aircraft"], queryFn: () => api<Aircraft[]>("/api/aircraft"), staleTime: 600_000 });
}
