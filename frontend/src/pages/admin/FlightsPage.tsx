import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowDown, ArrowUp, ArrowUpDown, Eye, MoreHorizontal, Pencil, Plus, Search, Trash2, Zap } from "lucide-react";
import { api, errorMessage, qs } from "@/lib/api";
import { PERMISSIONS, useAuth } from "@/lib/auth";
import { useAirports } from "@/lib/queries";
import { FLIGHT_STATUSES, type Flight, type Page } from "@/lib/types";
import { formatDateTime, formatDuration, formatMoney } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/dialog";
import { Input, Select } from "@/components/ui/form";
import { EmptyState, ErrorState, Menu, MenuContent, MenuItem, MenuTrigger, Pagination, Table, TableSkeleton, Td, Th } from "@/components/ui/misc";
import { PageHeader, StatusBadge } from "@/components/domain";
import { FlightFormDialog } from "./FlightFormDialog";
import { FlightStatusDialog } from "./FlightStatusDialog";

export default function FlightsPage() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const airports = useAirports();
  const [f, setF] = useState({ q: "", status: "", origin: "", destination: "", from: "", to: "" });
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<{ field: string; dir: "asc" | "desc" }>({ field: "departureTime", dir: "asc" });
  const [form, setForm] = useState<{ open: boolean; flight?: Flight | null }>({ open: false });
  const [statusFor, setStatusFor] = useState<Flight | null>(null);
  const [deleting, setDeleting] = useState<Flight | null>(null);

  const list = useQuery({
    queryKey: ["flights", f, page, sort],
    queryFn: () => api<Page<Flight>>(`/api/flights${qs({ ...f, page, size: 15, sort: sort.field, dir: sort.dir })}`),
    placeholderData: keepPreviousData,
  });
  const del = useMutation({
    mutationFn: (fl: Flight) => api(`/api/flights/${fl.id}`, { method: "DELETE" }),
    onSuccess: () => { toast.success("Flight deleted"); setDeleting(null); qc.invalidateQueries({ queryKey: ["flights"] }); },
    onError: (e) => { toast.error(errorMessage(e)); setDeleting(null); },
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setF({ ...f, [k]: e.target.value }); setPage(0); };
  const sortBy = (field: string) => setSort((s) => ({ field, dir: s.field === field && s.dir === "asc" ? "desc" : "asc" }));
  const SortIcon = ({ field }: { field: string }) => sort.field !== field ? <ArrowUpDown className="size-3" /> : sort.dir === "asc" ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />;
  const SortTh = ({ field, children }: { field: string; children: React.ReactNode }) => (
    <Th aria-sort={sort.field === field ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}>
      <button className="inline-flex items-center gap-1 uppercase" onClick={() => sortBy(field)}>{children}<SortIcon field={field} /></button>
    </Th>
  );
  const ap = airports.data?.map((a) => <option key={a.code} value={a.code}>{a.code}</option>);

  return (
    <>
      <PageHeader title="Flights" description="Schedule, edit and manage the operational status of every flight."
        crumbs={[{ label: "Console", to: "/admin" }, { label: "Flights" }]}
        actions={hasRole(...PERMISSIONS.flightsWrite) && <Button onClick={() => setForm({ open: true, flight: null })}><Plus />New flight</Button>} />
      <Card>
        <div className="grid gap-3 border-b border-navy-100 p-4 sm:grid-cols-2 lg:grid-cols-6">
          <div className="relative lg:col-span-2"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-navy-400" />
            <Input aria-label="Search flights" placeholder="Flight number or city" className="pl-9" value={f.q} onChange={set("q")} /></div>
          <Select aria-label="Status filter" value={f.status} onChange={set("status")}><option value="">All statuses</option>{FLIGHT_STATUSES.map((s) => <option key={s}>{s}</option>)}</Select>
          <div className="grid grid-cols-2 gap-2">
            <Select aria-label="Origin filter" value={f.origin} onChange={set("origin")}><option value="">From</option>{ap}</Select>
            <Select aria-label="Destination filter" value={f.destination} onChange={set("destination")}><option value="">To</option>{ap}</Select>
          </div>
          <Input aria-label="From date" type="date" value={f.from} onChange={set("from")} />
          <Input aria-label="To date" type="date" value={f.to} onChange={set("to")} />
        </div>
        {list.isLoading ? <TableSkeleton cols={7} /> : list.isError ? <ErrorState message={(list.error as Error).message} onRetry={() => list.refetch()} />
          : list.data!.content.length === 0 ? <EmptyState title="No flights match your filters" description="Adjust the filters or schedule a new flight."
            action={<Button variant="outline" onClick={() => setF({ q: "", status: "", origin: "", destination: "", from: "", to: "" })}>Clear filters</Button>} /> : (
          <Table>
            <thead><tr><SortTh field="flightNumber">Flight</SortTh><Th>Route</Th><SortTh field="departureTime">Departure (UTC)</SortTh><Th>Duration</Th><Th>Aircraft</Th><Th>Economy</Th><SortTh field="status">Status</SortTh><Th className="text-right"><span className="sr-only">Actions</span></Th></tr></thead>
            <tbody>{list.data!.content.map((fl) => (
              <tr key={fl.id} className="hover:bg-navy-50/50">
                <Td><Link className="font-bold text-accent-700 hover:underline" to={`/admin/flights/${fl.id}`}>{fl.flightNumber}</Link></Td>
                <Td>{fl.origin.code} → {fl.destination.code}</Td>
                <Td className="whitespace-nowrap">{formatDateTime(fl.departureTime, "UTC")}</Td>
                <Td>{formatDuration(fl.durationMinutes)}</Td>
                <Td className="text-xs">{fl.aircraft.model}<span className="block text-navy-500">{fl.capacity} seats</span></Td>
                <Td>{formatMoney(fl.economyFare, fl.currency)}</Td>
                <Td><StatusBadge status={fl.status} />{fl.delayMinutes > 0 && <span className="ml-1 text-xs text-amber-700">+{fl.delayMinutes}m</span>}</Td>
                <Td className="text-right">
                  <Menu><MenuTrigger asChild><Button variant="ghost" size="icon" aria-label={`Actions for ${fl.flightNumber}`}><MoreHorizontal /></Button></MenuTrigger>
                    <MenuContent>
                      <MenuItem onSelect={() => navigate(`/admin/flights/${fl.id}`)}><Eye />View details</MenuItem>
                      {hasRole(...PERMISSIONS.flightsWrite) && <MenuItem onSelect={() => setForm({ open: true, flight: fl })} disabled={["DEPARTED", "ARRIVED", "CANCELLED"].includes(fl.status)}><Pencil />Edit</MenuItem>}
                      {hasRole(...PERMISSIONS.flightsWrite) && <MenuItem onSelect={() => setStatusFor(fl)} disabled={["ARRIVED", "CANCELLED"].includes(fl.status)}><Zap />Change status</MenuItem>}
                      {hasRole(...PERMISSIONS.flightsDelete) && <MenuItem danger onSelect={() => setDeleting(fl)}><Trash2 />Delete</MenuItem>}
                    </MenuContent></Menu>
                </Td>
              </tr>))}</tbody>
          </Table>)}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />}
      </Card>
      <FlightFormDialog open={form.open} flight={form.flight} onClose={() => setForm({ open: false })} />
      <FlightStatusDialog flight={statusFor} onClose={() => setStatusFor(null)} />
      <ConfirmDialog open={!!deleting} onOpenChange={(o) => !o && setDeleting(null)} destructive title={`Delete ${deleting?.flightNumber}?`}
        confirmLabel="Delete flight" loading={del.isPending} onConfirm={() => deleting && del.mutate(deleting)}
        description="The flight will be permanently removed. Flights with active bookings cannot be deleted - cancel them instead so passengers are refunded and notified." />
    </>
  );
}
