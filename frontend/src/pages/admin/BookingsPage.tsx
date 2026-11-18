import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Search, XCircle } from "lucide-react";
import { api, errorMessage, qs } from "@/lib/api";
import type { Booking, Page } from "@/lib/types";
import { formatDateTime, formatMoney } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog, Dialog } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/form";
import { EmptyState, ErrorState, Pagination, Table, TableSkeleton, Td, Th } from "@/components/ui/misc";
import { PageHeader, StatusBadge } from "@/components/domain";
import { BookingSummary } from "@/components/BookingCard";

export default function BookingsPage() {
  const qc = useQueryClient();
  const [f, setF] = useState({ q: "", status: "", from: "", to: "" });
  const [page, setPage] = useState(0);
  const [view, setView] = useState<Booking | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");
  const list = useQuery({ queryKey: ["bookings", f, page], queryFn: () => api<Page<Booking>>(`/api/bookings${qs({ ...f, page, size: 15 })}`), placeholderData: keepPreviousData });
  const cancel = useMutation({
    mutationFn: () => api<Booking>(`/api/bookings/${view!.pnr}/cancel`, { method: "POST", body: { reason: reason || "Cancelled by airline agent" } }),
    onSuccess: (b) => { toast.success(`${b.pnr} cancelled and refunded`); setView(b); setCancelling(false); qc.invalidateQueries({ queryKey: ["bookings"] }); },
    onError: (e) => { toast.error(errorMessage(e)); setCancelling(false); },
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setF({ ...f, [k]: e.target.value }); setPage(0); };
  return (
    <>
      <PageHeader title="Bookings" description="Search reservations, view itineraries and process cancellations." crumbs={[{ label: "Console", to: "/admin" }, { label: "Bookings" }]} />
      <Card>
        <div className="grid gap-3 border-b border-navy-100 p-4 sm:grid-cols-2 lg:grid-cols-5">
          <div className="relative lg:col-span-2"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-navy-400" />
            <Input aria-label="Search bookings" placeholder="PNR, e-mail or flight number" className="pl-9" value={f.q} onChange={set("q")} /></div>
          <Select aria-label="Status filter" value={f.status} onChange={set("status")}><option value="">All statuses</option><option>CONFIRMED</option><option>CANCELLED</option></Select>
          <Input type="date" aria-label="Booked from" value={f.from} onChange={set("from")} />
          <Input type="date" aria-label="Booked to" value={f.to} onChange={set("to")} />
        </div>
        {list.isLoading ? <TableSkeleton /> : list.isError ? <ErrorState message={(list.error as Error).message} onRetry={() => list.refetch()} />
          : list.data!.content.length === 0 ? <EmptyState title="No bookings found" description="Bookings appear here as soon as they are made in the portal or by agents." /> : (
          <Table><thead><tr><Th>PNR</Th><Th>Flight</Th><Th>Departure</Th><Th>Contact</Th><Th>Pax</Th><Th>Total</Th><Th>Channel</Th><Th>Status</Th><Th>Booked</Th></tr></thead>
            <tbody>{list.data!.content.map((b) => (
              <tr key={b.id} className="cursor-pointer hover:bg-navy-50/50" onClick={() => setView(b)}>
                <Td><button className="font-mono font-bold text-accent-700 hover:underline" onClick={() => setView(b)}>{b.pnr}</button></Td>
                <Td>{b.flightNumber} <span className="text-xs text-navy-500">{b.origin}→{b.destination}</span></Td>
                <Td className="whitespace-nowrap">{formatDateTime(b.departureTime)}</Td><Td>{b.contactEmail}</Td><Td>{b.passengers.length}</Td>
                <Td>{formatMoney(b.totalAmount, b.currency)}</Td><Td className="text-xs">{b.channel}</Td><Td><StatusBadge status={b.status} /></Td>
                <Td className="text-xs text-navy-500">{formatDateTime(b.createdAt)}</Td>
              </tr>))}</tbody></Table>)}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />}
      </Card>
      <Dialog open={!!view} onOpenChange={(o) => !o && setView(null)} wide title={`Booking ${view?.pnr ?? ""}`}
        description={view ? `Created ${formatDateTime(view.createdAt)} via ${view.channel}` : undefined}
        footer={view?.status === "CONFIRMED" && new Date(view.departureTime) > new Date() && <Button variant="danger" onClick={() => setCancelling(true)}><XCircle />Cancel & refund</Button>}>
        {view && <div className="space-y-4"><BookingSummary booking={view} />
          <div className="text-sm"><p className="font-semibold">Payments</p>
            <ul className="mt-1 space-y-1 text-navy-600">{view.payments.map((p) => <li key={p.reference}>{p.type} {formatMoney(p.amount, view.currency)} · {p.status} · <span className="font-mono text-xs">{p.reference}</span></li>)}</ul></div></div>}
      </Dialog>
      <ConfirmDialog open={cancelling} onOpenChange={setCancelling} destructive title={`Cancel ${view?.pnr}?`} confirmLabel="Cancel & refund" loading={cancel.isPending}
        description={`All seats are released and ${formatMoney(view?.totalAmount, view?.currency)} is refunded. The customer is notified by e-mail and SMS.`} onConfirm={() => cancel.mutate()}>
        <Field label="Reason" htmlFor="cancel-reason"><Input id="cancel-reason" value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Customer request by phone" /></Field>
      </ConfirmDialog>
    </>
  );
}
