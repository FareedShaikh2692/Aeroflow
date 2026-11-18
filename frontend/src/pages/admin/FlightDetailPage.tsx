import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Pencil, Zap } from "lucide-react";
import { api } from "@/lib/api";
import { PERMISSIONS, useAuth } from "@/lib/auth";
import type { BookingPassenger, Flight, SeatMap as SeatMapT } from "@/lib/types";
import { formatDateTime, formatMoney } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardBody } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorState, Skeleton, Table, Tabs, Td, Th } from "@/components/ui/misc";
import { PageHeader, RouteLine, StatusBadge } from "@/components/domain";
import { SeatMap } from "@/components/SeatMap";
import { FlightFormDialog } from "./FlightFormDialog";
import { FlightStatusDialog } from "./FlightStatusDialog";

export default function FlightDetailPage() {
  const { id = "" } = useParams();
  const { hasRole } = useAuth();
  const [tab, setTab] = useState("manifest");
  const [edit, setEdit] = useState(false);
  const [status, setStatus] = useState(false);
  const flight = useQuery({ queryKey: ["flight", id], queryFn: () => api<Flight>(`/api/flights/${id}`) });
  const manifest = useQuery({ queryKey: ["manifest", id], queryFn: () => api<BookingPassenger[]>(`/api/bookings/flight/${id}/manifest`) });
  const seats = useQuery({ queryKey: ["seatmap", id], queryFn: () => api<SeatMapT>(`/api/seat-maps/${id}`), enabled: tab === "seats" });
  if (flight.isLoading) return <Skeleton className="h-64 w-full" />;
  if (flight.isError || !flight.data) return <Card><ErrorState message={(flight.error as Error)?.message ?? "Not found"} onRetry={() => flight.refetch()} /></Card>;
  const f = flight.data;
  const booked = manifest.data?.length ?? 0;
  const canWrite = hasRole(...PERMISSIONS.flightsWrite);
  return (
    <>
      <PageHeader title={`${f.flightNumber} · ${f.origin.city} → ${f.destination.city}`} description={`${f.aircraft.manufacturer} ${f.aircraft.model} · ${f.aircraft.registration}`}
        crumbs={[{ label: "Console", to: "/admin" }, { label: "Flights", to: "/admin/flights" }, { label: f.flightNumber }]}
        actions={canWrite && <>
          <Button variant="outline" onClick={() => setEdit(true)} disabled={["DEPARTED", "ARRIVED", "CANCELLED"].includes(f.status)}><Pencil />Edit</Button>
          <Button onClick={() => setStatus(true)} disabled={["ARRIVED", "CANCELLED"].includes(f.status)}><Zap />Change status</Button>
        </>} />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Card><CardBody><RouteLine origin={f.origin.code} destination={f.destination.code} departure={f.departureTime} arrival={f.arrivalTime} duration={f.durationMinutes} originTz={f.origin.timezone} destTz={f.destination.timezone} /></CardBody></Card>
        <Card><CardBody className="grid grid-cols-2 gap-3 text-sm">
          <div><p className="text-xs text-navy-500">Status</p><StatusBadge status={f.status} /></div>
          <div><p className="text-xs text-navy-500">Gate / Terminal</p><p className="font-bold">{f.gate ?? "—"} / {f.terminal ?? "—"}</p></div>
          <div><p className="text-xs text-navy-500">Load</p><p className="font-bold">{booked}/{f.capacity} ({Math.round((booked / f.capacity) * 100)}%)</p></div>
          <div><p className="text-xs text-navy-500">Fares</p><p className="font-bold">{formatMoney(f.economyFare)} / {formatMoney(f.businessFare)}</p></div>
          {f.statusReason && <div className="col-span-2"><p className="text-xs text-navy-500">Reason</p><p>{f.statusReason}</p></div>}
          {f.delayMinutes > 0 && <div className="col-span-2 text-amber-700">Delayed {f.delayMinutes} min · ETD {formatDateTime(f.estimatedDepartureTime)}</div>}
        </CardBody></Card>
      </div>
      <div className="mt-6">
        <Tabs value={tab} onValueChange={setTab} tabs={[{ value: "manifest", label: `Passenger manifest (${booked})` }, { value: "seats", label: "Seat map" }]} />
        <Card className="mt-4">
          {tab === "manifest" ? (
            manifest.isLoading ? <CardBody><Skeleton className="h-40" /></CardBody> : !manifest.data?.length ? <EmptyState title="No passengers booked yet" /> : (
              <Table><thead><tr><Th>Seat</Th><Th>Passenger</Th><Th>Cabin</Th><Th>Passport</Th><Th>Check-in</Th></tr></thead>
                <tbody>{manifest.data.map((p) => (
                  <tr key={p.id}><Td className="font-mono font-bold">{p.seatNumber}</Td><Td>{p.lastName.toUpperCase()}, {p.firstName}</Td>
                    <Td><Badge tone={p.cabin === "BUSINESS" ? "gold" : "blue"}>{p.cabin}</Badge></Td><Td className="font-mono text-xs">{p.passportMasked ?? "—"}</Td>
                    <Td>{p.checkedIn ? <Badge tone="green" dot>Checked in</Badge> : <Badge>Not yet</Badge>}</Td></tr>))}</tbody></Table>)
          ) : (
            <CardBody>{seats.data ? <SeatMap {...seats.data} cabin="ECONOMY" selected={{}} onSelect={() => undefined} readOnly /> : <Skeleton className="h-96" />}</CardBody>
          )}
        </Card>
      </div>
      <FlightFormDialog open={edit} flight={f} onClose={() => setEdit(false)} />
      <FlightStatusDialog flight={status ? f : null} onClose={() => setStatus(false)} />
    </>
  );
}
