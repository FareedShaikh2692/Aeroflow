import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ChevronRight, Plane } from "lucide-react";
import { Badge } from "./ui/badge";
import type { FlightStatus } from "@/lib/types";
import { cn, formatDate, formatDuration, formatTime } from "@/lib/utils";

const STATUS_TONE: Record<string, "neutral" | "blue" | "green" | "amber" | "red" | "violet"> = {
  SCHEDULED: "blue", DELAYED: "amber", BOARDING: "violet", DEPARTED: "green", ARRIVED: "neutral", CANCELLED: "red",
  CONFIRMED: "green",
};

export function StatusBadge({ status }: { status: FlightStatus | string }) {
  return <Badge tone={STATUS_TONE[status] ?? "neutral"} dot>{status.charAt(0) + status.slice(1).toLowerCase()}</Badge>;
}

export function PageHeader({ title, description, crumbs = [], actions }: {
  title: string; description?: string; crumbs?: { label: string; to?: string }[]; actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        {crumbs.length > 0 && (
          <nav aria-label="Breadcrumb" className="mb-2">
            <ol className="flex flex-wrap items-center gap-1 text-xs font-medium text-navy-500">
              {crumbs.map((c, i) => (
                <li key={c.label} className="flex items-center gap-1">
                  {c.to ? <Link to={c.to} className="hover:text-navy-900">{c.label}</Link> : <span aria-current="page" className="text-navy-700">{c.label}</span>}
                  {i < crumbs.length - 1 && <ChevronRight className="size-3" aria-hidden />}
                </li>
              ))}
            </ol>
          </nav>
        )}
        <h1 className="text-2xl font-extrabold text-navy-900 sm:text-[1.7rem]">{title}</h1>
        {description && <p className="mt-1 text-sm text-navy-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, hint, icon, tone = "navy" }: {
  label: string; value: ReactNode; hint?: ReactNode; icon: ReactNode; tone?: "navy" | "blue" | "amber" | "red" | "green";
}) {
  const tones = { navy: "bg-navy-900 text-white", blue: "bg-accent-50 text-accent-600", amber: "bg-amber-50 text-amber-600",
    red: "bg-red-50 text-red-600", green: "bg-emerald-50 text-emerald-600" };
  return (
    <div className="rounded-xl border border-navy-100 bg-white p-5 shadow-card">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-navy-500">{label}</p>
          <p className="mt-2 font-display text-2xl font-extrabold text-navy-900">{value}</p>
          {hint && <p className="mt-1 text-xs text-navy-500">{hint}</p>}
        </div>
        <div className={cn("rounded-xl p-2.5 [&_svg]:size-5", tones[tone])}>{icon}</div>
      </div>
    </div>
  );
}

/** DXB 08:15 ——✈—— 13:05 LHR, shown in each airport's local time. */
export function RouteLine({ origin, destination, departure, arrival, duration, originTz, destTz, compact }: {
  origin: string; destination: string; departure: string; arrival: string; duration?: number;
  originTz?: string; destTz?: string; compact?: boolean;
}) {
  return (
    <div className="flex items-center gap-3 sm:gap-5">
      <div className="text-left">
        <p className={cn("font-display font-extrabold text-navy-900", compact ? "text-lg" : "text-2xl")}>{formatTime(departure, originTz)}</p>
        <p className="text-xs font-semibold text-navy-500">{origin} · {formatDate(departure, originTz)}</p>
      </div>
      <div className="flex flex-1 flex-col items-center gap-1 text-navy-400">
        {duration != null && <span className="text-[11px] font-semibold">{formatDuration(duration)}</span>}
        <div className="flex w-full items-center gap-1">
          <span className="h-px flex-1 border-t border-dashed border-navy-300" />
          <Plane className="size-4 text-accent-600" aria-hidden />
          <span className="h-px flex-1 border-t border-dashed border-navy-300" />
        </div>
        <span className="text-[11px]">Non-stop</span>
      </div>
      <div className="text-right">
        <p className={cn("font-display font-extrabold text-navy-900", compact ? "text-lg" : "text-2xl")}>{formatTime(arrival, destTz)}</p>
        <p className="text-xs font-semibold text-navy-500">{destination} · {formatDate(arrival, destTz)}</p>
      </div>
    </div>
  );
}

export function Logo({ light }: { light?: boolean }) {
  return (
    <span className="flex items-center gap-2.5">
      <svg viewBox="0 0 32 32" className="size-8" aria-hidden>
        <rect width="32" height="32" rx="8" fill={light ? "#ffffff" : "#0b1f3a"} />
        <path d="M7 19.5 25 11l-4.5 9.5-4-2.5-3 4-1-5.5z" fill={light ? "#0b1f3a" : "#fff"} />
        <path d="m16.5 18 4 2.5L25 11z" fill="#3b82f6" />
      </svg>
      <span className={cn("font-display text-lg font-extrabold tracking-tight", light ? "text-white" : "text-navy-900")}>
        Aero<span className="text-accent-500">Flow</span>
      </span>
    </span>
  );
}
