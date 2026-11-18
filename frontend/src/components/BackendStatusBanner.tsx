import { useQuery } from "@tanstack/react-query";
import { Info } from "lucide-react";
import { ApiError, BACKEND_UNAVAILABLE_MESSAGE, api } from "@/lib/api";
import type { Airport } from "@/lib/types";

/** Explains up-front when the deployment has no reachable API (e.g. frontend-only preview). */
export function BackendStatusBanner() {
  const q = useQuery({
    queryKey: ["airports"],
    queryFn: () => api<Airport[]>("/api/airports", { auth: false }),
    staleTime: 3_600_000,
    retry: false,
  });
  const down = q.isError && (!(q.error instanceof ApiError) || q.error.code === "BACKEND_UNAVAILABLE" || q.error.status === 0 || q.error.status >= 500);
  if (!down) return null;
  return (
    <div role="status" className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-center text-sm text-amber-900">
      <Info className="mr-1.5 inline size-4 -translate-y-px" aria-hidden />
      {BACKEND_UNAVAILABLE_MESSAGE} Please try again shortly.
    </div>
  );
}
