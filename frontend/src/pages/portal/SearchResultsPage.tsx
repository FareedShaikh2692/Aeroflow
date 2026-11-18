import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Armchair, Clock, Plane } from "lucide-react";
import { api, qs } from "@/lib/api";
import type { Cabin, Flight } from "@/lib/types";
import { formatMoney } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/misc";
import { RouteLine, StatusBadge } from "@/components/domain";
import { SearchForm } from "./HomePage";

export default function SearchResultsPage() {
  const [params] = useSearchParams();
  const origin = params.get("origin") ?? "DXB";
  const destination = params.get("destination") ?? "LHR";
  const date = params.get("date") ?? "";
  const passengers = Number(params.get("passengers") ?? 1);
  const cabin = (params.get("cabin") ?? "ECONOMY") as Cabin;
  const q = useQuery({
    queryKey: ["search", origin, destination, date],
    queryFn: () => api<Flight[]>(`/api/flights/search${qs({ origin, destination, date })}`, { auth: false }),
    enabled: !!date,
  });

  return (
    <div>
      <div className="hero-sky px-4 py-8 sm:px-6">
        <div className="mx-auto max-w-7xl"><SearchForm initial={{ origin, destination, date, passengers, cabin }} /></div>
      </div>
      <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        <h1 className="text-xl font-extrabold">{origin} → {destination}</h1>
        <p className="text-sm text-navy-500">{date} · {passengers} traveller{passengers > 1 ? "s" : ""} · {cabin === "BUSINESS" ? "Business" : "Economy"}</p>
        <div className="mt-6 space-y-4">
          {q.isLoading && [0, 1].map((i) => <Card key={i} className="p-6"><Skeleton className="h-16 w-full" /></Card>)}
          {q.isError && <Card><ErrorState message={(q.error as Error).message} onRetry={() => q.refetch()} /></Card>}
          {q.data?.length === 0 && (
            <Card><EmptyState icon={<Plane className="size-7" />} title="No flights on this date"
              description="Try another date - AeroFlow operates daily services from Dubai to every destination." /></Card>
          )}
          {q.data?.map((f) => {
            const fare = cabin === "BUSINESS" ? f.businessFare : f.economyFare;
            return (
              <Card key={f.id} className="overflow-hidden transition hover:shadow-lift" data-testid="flight-result">
                <div className="grid gap-5 p-5 sm:p-6 md:grid-cols-[1fr_auto] md:items-center">
                  <div>
                    <div className="mb-3 flex flex-wrap items-center gap-2 text-xs text-navy-500">
                      <span className="font-bold text-navy-800">{f.flightNumber}</span>·<span>{f.aircraft.manufacturer} {f.aircraft.model}</span>
                      <StatusBadge status={f.status} />
                      {f.delayMinutes > 0 && <span className="flex items-center gap-1 text-amber-700"><Clock className="size-3.5" />+{f.delayMinutes} min</span>}
                    </div>
                    <RouteLine origin={f.origin.code} destination={f.destination.code} departure={f.departureTime} arrival={f.arrivalTime}
                      duration={f.durationMinutes} originTz={f.origin.timezone} destTz={f.destination.timezone} />
                  </div>
                  <div className="flex items-center justify-between gap-6 border-t border-navy-100 pt-4 md:flex-col md:items-end md:border-l md:border-t-0 md:pl-6 md:pt-0">
                    <div className="text-right">
                      <p className="text-xs text-navy-500">{cabin === "BUSINESS" ? "Business" : "Economy"} from</p>
                      <p className="font-display text-2xl font-extrabold">{formatMoney(fare, f.currency)}</p>
                      <p className="text-xs text-navy-500">per traveller</p>
                    </div>
                    <Button asChild variant="accent"><Link to={`/book/${f.id}?passengers=${passengers}&cabin=${cabin}`}><Armchair />Select</Link></Button>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}
