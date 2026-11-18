import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { api, qs } from "@/lib/api";
import { PERMISSIONS, useAuth } from "@/lib/auth";
import type { Flight, FlightStats, Page } from "@/lib/types";
import { formatTime, todayIso } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { EmptyState, ErrorState, Table, TableSkeleton, Td, Th } from "@/components/ui/misc";
import { PageHeader, StatCard, StatusBadge } from "@/components/domain";
import { FlightStatusDialog } from "./FlightStatusDialog";
import { CalendarClock, Plane, PlaneLanding, PlaneTakeoff } from "lucide-react";

export default function OperationsPage() {
  const { hasRole } = useAuth();
  const [day, setDay] = useState(0);
  const [editing, setEditing] = useState<Flight | null>(null);
  const date = todayIso(day);
  const stats = useQuery({ queryKey: ["flight-stats"], queryFn: () => api<FlightStats>("/api/flights/stats"), refetchInterval: 30_000 });
  const board = useQuery({
    queryKey: ["ops-board", date],
    queryFn: () => api<Page<Flight>>(`/api/flights${qs({ from: date, to: date, size: 100, sort: "departureTime" })}`),
    refetchInterval: 30_000,
  });
  const canEdit = hasRole(...PERMISSIONS.flightsWrite);
  return (
    <>
      <PageHeader title="Operations control" description="Live departure board for the DXB network. Auto-refreshes every 30 seconds."
        crumbs={[{ label: "Console", to: "/admin" }, { label: "Operations" }]}
        actions={<>
          {[-1, 0, 1].map((d) => <Button key={d} variant={d === day ? "primary" : "outline"} size="sm" onClick={() => setDay(d)}>{d === -1 ? "Yesterday" : d === 0 ? "Today" : "Tomorrow"}</Button>)}
          <Button variant="outline" size="sm" onClick={() => board.refetch()} loading={board.isFetching}><RefreshCw />Refresh</Button>
        </>} />
      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Departing today" value={stats.data?.departingToday ?? "—"} icon={<Plane />} />
        <StatCard label="Boarding / airborne" value={stats.data?.activeNow ?? "—"} icon={<PlaneTakeoff />} tone="green" />
        <StatCard label="Delayed" value={stats.data?.delayed ?? "—"} icon={<CalendarClock />} tone="amber" />
        <StatCard label="Arrived (all time)" value={stats.data?.arrived ?? "—"} icon={<PlaneLanding />} tone="blue" />
      </div>
      <Card>
        <CardHeader title={`Departures · ${date}`} description="Times shown in UTC" />
        {board.isLoading ? <TableSkeleton /> : board.isError ? <ErrorState message={(board.error as Error).message} onRetry={() => board.refetch()} />
          : board.data!.content.length === 0 ? <EmptyState title="No flights on this day" /> : (
          <Table>
            <thead><tr><Th>STD</Th><Th>Flight</Th><Th>Route</Th><Th>Aircraft</Th><Th>Gate</Th><Th>Status</Th><Th>ETD</Th><Th className="text-right">Action</Th></tr></thead>
            <tbody>{board.data!.content.map((f) => (
              <tr key={f.id} className="hover:bg-navy-50/50">
                <Td className="font-mono font-semibold">{formatTime(f.departureTime, "UTC")}</Td>
                <Td><Link to={`/admin/flights/${f.id}`} className="font-bold text-accent-700 hover:underline">{f.flightNumber}</Link></Td>
                <Td>{f.origin.code} → {f.destination.code}<span className="ml-1 text-xs text-navy-500">{f.destination.city}</span></Td>
                <Td className="text-xs">{f.aircraft.model} · {f.aircraft.registration}</Td>
                <Td>{f.gate ?? "—"}</Td>
                <Td><StatusBadge status={f.status} /></Td>
                <Td className="font-mono">{f.delayMinutes ? <span className="text-amber-700">{formatTime(f.estimatedDepartureTime, "UTC")}</span> : "On time"}</Td>
                <Td className="text-right">{canEdit && <Button size="sm" variant="outline" onClick={() => setEditing(f)} disabled={f.status === "ARRIVED" || f.status === "CANCELLED"}>Update</Button>}</Td>
              </tr>))}</tbody>
          </Table>)}
      </Card>
      <FlightStatusDialog flight={editing} onClose={() => setEditing(null)} />
    </>
  );
}
