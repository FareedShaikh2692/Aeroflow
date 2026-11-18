import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Trash2 } from "lucide-react";
import { api, errorMessage } from "@/lib/api";
import { PERMISSIONS, useAuth } from "@/lib/auth";
import type { PassengerDetail } from "@/lib/types";
import { formatDateTime, formatMoney, formatNumber } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorState, Skeleton, Table, Td, Th } from "@/components/ui/misc";
import { PageHeader, StatCard, StatusBadge } from "@/components/domain";
import { PassengerFormDialog } from "./PassengersPage";
import { Award, Plane, Wallet } from "lucide-react";

export default function PassengerDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [edit, setEdit] = useState(false);
  const [del, setDel] = useState(false);
  const q = useQuery({ queryKey: ["passenger", id], queryFn: () => api<PassengerDetail>(`/api/passengers/${id}`) });
  const remove = useMutation({
    mutationFn: () => api(`/api/passengers/${id}`, { method: "DELETE" }),
    onSuccess: () => { toast.success("Passenger profile deleted"); navigate("/admin/passengers"); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  if (q.isLoading) return <Skeleton className="h-64" />;
  if (q.isError || !q.data) return <Card><ErrorState message={(q.error as Error)?.message} onRetry={() => q.refetch()} /></Card>;
  const { profile: p, travelHistory, totalTrips, upcomingTrips, lifetimeSpend } = q.data;
  const row = (label: string, v?: string | null) => <div><dt className="text-xs text-navy-500">{label}</dt><dd className="font-semibold">{v || "—"}</dd></div>;
  return (
    <>
      <PageHeader title={`${p.firstName} ${p.lastName}`} description={p.email}
        crumbs={[{ label: "Console", to: "/admin" }, { label: "Passengers", to: "/admin/passengers" }, { label: `${p.firstName} ${p.lastName}` }]}
        actions={<>
          {hasRole(...PERMISSIONS.passengersWrite) && <Button variant="outline" onClick={() => setEdit(true)}><Pencil />Edit</Button>}
          {hasRole("SUPER_ADMIN", "ADMIN") && <Button variant="outline" className="text-red-600" onClick={() => setDel(true)}><Trash2 />Delete</Button>}
        </>} />
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatCard label="Trips" value={totalTrips} hint={`${upcomingTrips} upcoming`} icon={<Plane />} />
        <StatCard label="Lifetime spend" value={formatMoney(lifetimeSpend)} icon={<Wallet />} tone="blue" />
        <StatCard label={`${p.loyaltyTier} tier`} value={formatNumber(p.loyaltyPoints)} hint="Loyalty points" icon={<Award />} tone="amber" />
      </div>
      <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
        <Card><CardHeader title="Profile" /><CardBody><dl className="grid grid-cols-2 gap-4 text-sm">
          {row("Phone", p.phone)}{row("Date of birth", p.dateOfBirth)}{row("Nationality", p.nationality)}{row("Passport", p.passportNumber)}
          {row("Passport expiry", p.passportExpiry)}{row("City", p.city)}{row("Country", p.country)}{row("Account", p.userId ? "Registered member" : "Staff-created")}
          <div className="col-span-2">{row("Notes", p.notes)}</div></dl></CardBody></Card>
        <Card><CardHeader title="Travel & booking history" description="Built from booking and check-in events" />
          {travelHistory.length === 0 ? <EmptyState title="No trips yet" /> : (
            <Table><thead><tr><Th>PNR</Th><Th>Flight</Th><Th>Route</Th><Th>Departure</Th><Th>Amount</Th><Th>Status</Th></tr></thead>
              <tbody>{travelHistory.map((t) => (
                <tr key={t.bookingId + t.pnr}><Td className="font-mono font-bold">{t.pnr}</Td><Td>{t.flightNumber}</Td><Td>{t.origin} → {t.destination}</Td>
                  <Td>{formatDateTime(t.departureTime)}</Td><Td>{t.amount != null ? formatMoney(t.amount, t.currency) : "—"}</Td>
                  <Td className="flex gap-1"><StatusBadge status={t.status} />{t.checkedIn && <Badge tone="green">Checked in</Badge>}</Td></tr>))}</tbody></Table>)}
        </Card>
      </div>
      <PassengerFormDialog open={edit} passenger={p} onClose={() => setEdit(false)} />
      <ConfirmDialog open={del} onOpenChange={setDel} destructive title="Delete passenger profile?" confirmLabel="Delete" loading={remove.isPending}
        description="The profile is removed from the passenger directory. Existing bookings are not affected. This cannot be undone." onConfirm={() => remove.mutate()} />
    </>
  );
}
