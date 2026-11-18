import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { toast } from "sonner";
import { Plus, Search } from "lucide-react";
import { ApiError, api, errorMessage, qs } from "@/lib/api";
import { PERMISSIONS, useAuth } from "@/lib/auth";
import { clean, passengerProfileSchema } from "@/lib/schemas";
import type { Page, Passenger } from "@/lib/types";
import { formatDateTime, formatNumber } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorState, Pagination, Table, TableSkeleton, Td, Th } from "@/components/ui/misc";
import { PageHeader } from "@/components/domain";

type Values = z.infer<typeof passengerProfileSchema>;

export function PassengerFormDialog({ open, passenger, onClose }: { open: boolean; passenger?: Passenger | null; onClose: () => void }) {
  const qc = useQueryClient();
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<Values>({ resolver: zodResolver(passengerProfileSchema) });
  useEffect(() => {
    if (open) reset({ firstName: passenger?.firstName ?? "", lastName: passenger?.lastName ?? "", email: passenger?.email ?? "", phone: passenger?.phone ?? "",
      dateOfBirth: passenger?.dateOfBirth ?? "", nationality: passenger?.nationality ?? "", passportNumber: passenger?.passportNumber ?? "",
      passportExpiry: passenger?.passportExpiry ?? "", city: passenger?.city ?? "", country: passenger?.country ?? "", notes: passenger?.notes ?? "" });
  }, [open, passenger, reset]);
  const m = useMutation({
    mutationFn: (v: Values) => passenger ? api<Passenger>(`/api/passengers/${passenger.id}`, { method: "PUT", body: clean(v) })
      : api<Passenger>("/api/passengers", { method: "POST", body: clean(v) }),
    onSuccess: (p) => { toast.success(`${p.firstName} ${p.lastName} saved`); qc.invalidateQueries({ queryKey: ["passengers"] }); qc.invalidateQueries({ queryKey: ["passenger", p.id] }); onClose(); },
    onError: (e) => { if (e instanceof ApiError) e.fieldErrors.forEach((fe) => setError(fe.field as keyof Values, { message: fe.message })); toast.error(errorMessage(e)); },
  });
  const F = (name: keyof Values, label: string, type = "text") => (
    <Field label={label} htmlFor={`p-${name}`} error={errors[name]?.message}><Input id={`p-${name}`} type={type} aria-invalid={!!errors[name]} {...register(name)} /></Field>
  );
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} wide title={passenger ? "Edit passenger" : "Register passenger"}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button loading={m.isPending} onClick={handleSubmit((v) => m.mutate(v))}>Save passenger</Button></>}>
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit((v) => m.mutate(v))} aria-label="Passenger form">
        {F("firstName", "First name")}{F("lastName", "Last name")}{F("email", "E-mail", "email")}{F("phone", "Phone", "tel")}
        {F("dateOfBirth", "Date of birth", "date")}{F("nationality", "Nationality (ISO)")}{F("passportNumber", "Passport number")}{F("passportExpiry", "Passport expiry", "date")}
        {F("city", "City")}{F("country", "Country")}
        <Field label="Staff notes" htmlFor="p-notes" className="sm:col-span-2" error={errors.notes?.message}><Textarea id="p-notes" {...register("notes")} /></Field>
        <button type="submit" className="hidden" />
      </form>
    </Dialog>
  );
}

const TIER_TONE: Record<string, "gold" | "neutral" | "blue"> = { GOLD: "gold", SILVER: "neutral", BLUE: "blue" };

export default function PassengersPage() {
  const { hasRole } = useAuth();
  const [q, setQ] = useState("");
  const [tier, setTier] = useState("");
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(false);
  const list = useQuery({ queryKey: ["passengers", q, tier, page], queryFn: () => api<Page<Passenger>>(`/api/passengers${qs({ q, tier, page, size: 20 })}`), placeholderData: keepPreviousData });
  return (
    <>
      <PageHeader title="Passengers" description="Profiles, contact information and loyalty." crumbs={[{ label: "Console", to: "/admin" }, { label: "Passengers" }]}
        actions={hasRole(...PERMISSIONS.passengersWrite) && <Button onClick={() => setOpen(true)}><Plus />Register passenger</Button>} />
      <Card>
        <div className="grid gap-3 border-b border-navy-100 p-4 sm:grid-cols-[1fr_200px]">
          <div className="relative"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-navy-400" />
            <Input aria-label="Search passengers" placeholder="Name, e-mail, phone or passport" className="pl-9" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} /></div>
          <Select aria-label="Tier filter" value={tier} onChange={(e) => { setTier(e.target.value); setPage(0); }}><option value="">All tiers</option><option>BLUE</option><option>SILVER</option><option>GOLD</option></Select>
        </div>
        {list.isLoading ? <TableSkeleton /> : list.isError ? <ErrorState message={(list.error as Error).message} onRetry={() => list.refetch()} />
          : list.data!.content.length === 0 ? <EmptyState title="No passengers found" /> : (
          <Table><thead><tr><Th>Name</Th><Th>E-mail</Th><Th>Phone</Th><Th>Nationality</Th><Th>Tier</Th><Th>Points</Th><Th>Since</Th></tr></thead>
            <tbody>{list.data!.content.map((p) => (
              <tr key={p.id} className="hover:bg-navy-50/50">
                <Td><Link to={`/admin/passengers/${p.id}`} className="font-semibold text-accent-700 hover:underline">{p.firstName} {p.lastName}</Link>{p.userId && <Badge tone="blue" className="ml-2">Member</Badge>}</Td>
                <Td>{p.email}</Td><Td>{p.phone ?? "—"}</Td><Td>{p.nationality ?? "—"}</Td>
                <Td><Badge tone={TIER_TONE[p.loyaltyTier] ?? "neutral"}>{p.loyaltyTier}</Badge></Td><Td>{formatNumber(p.loyaltyPoints)}</Td>
                <Td className="text-xs text-navy-500">{formatDateTime(p.createdAt)}</Td>
              </tr>))}</tbody></Table>)}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />}
      </Card>
      <PassengerFormDialog open={open} onClose={() => setOpen(false)} />
    </>
  );
}
