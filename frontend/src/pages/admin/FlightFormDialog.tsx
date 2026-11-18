import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError, errorMessage } from "@/lib/api";
import { useAircraft, useAirports } from "@/lib/queries";
import { flightSchema } from "@/lib/schemas";
import type { Flight } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/form";

type Values = z.infer<typeof flightSchema>;
const toLocal = (iso: string) => new Date(iso).toISOString().slice(0, 16); // UTC wall time for datetime-local
const toIso = (local: string) => new Date(local + ":00Z").toISOString();

export function FlightFormDialog({ open, flight, onClose }: { open: boolean; flight?: Flight | null; onClose: () => void }) {
  const qc = useQueryClient();
  const airports = useAirports();
  const aircraft = useAircraft();
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<Values>({ resolver: zodResolver(flightSchema) });

  useEffect(() => {
    if (!open) return;
    const dep = new Date(Date.now() + 7 * 86400_000);
    dep.setUTCMinutes(0, 0, 0);
    reset(flight ? {
      flightNumber: flight.flightNumber, originCode: flight.origin.code, destinationCode: flight.destination.code,
      aircraftId: flight.aircraft.id, departureTime: toLocal(flight.departureTime), arrivalTime: toLocal(flight.arrivalTime),
      economyFare: flight.economyFare, businessFare: flight.businessFare, gate: flight.gate ?? "", terminal: flight.terminal ?? "",
    } : { flightNumber: "", originCode: "DXB", destinationCode: "LHR", aircraftId: "", departureTime: toLocal(dep.toISOString()),
      arrivalTime: toLocal(new Date(dep.getTime() + 7.5 * 3600_000).toISOString()), economyFare: 2500, businessFare: 8000, gate: "", terminal: "3" });
  }, [open, flight, reset]);

  const m = useMutation({
    mutationFn: (v: Values) => {
      const body = { ...v, departureTime: toIso(v.departureTime), arrivalTime: toIso(v.arrivalTime), gate: v.gate || undefined, terminal: v.terminal || undefined };
      return flight
        ? api<Flight>(`/api/flights/${flight.id}`, { method: "PUT", body, headers: { "If-Match": `"${flight.version}"` } })
        : api<Flight>("/api/flights", { method: "POST", body });
    },
    onSuccess: (f) => {
      toast.success(flight ? `${f.flightNumber} updated` : `${f.flightNumber} created`);
      qc.invalidateQueries({ queryKey: ["flights"] });
      qc.invalidateQueries({ queryKey: ["flight", f.id] });
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiError) e.fieldErrors.forEach((fe) => setError(fe.field as keyof Values, { message: fe.message }));
      toast.error(errorMessage(e));
    },
  });

  const ap = airports.data?.map((a) => <option key={a.code} value={a.code}>{a.code} · {a.city}</option>);
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} wide title={flight ? `Edit ${flight.flightNumber}` : "Schedule a new flight"}
      description="Times are entered in UTC. Fares in AED."
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button loading={m.isPending} onClick={handleSubmit((v) => m.mutate(v))}>{flight ? "Save changes" : "Create flight"}</Button></>}>
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit((v) => m.mutate(v))} aria-label="Flight form">
        <Field label="Flight number" htmlFor="flightNumber" error={errors.flightNumber?.message}><Input id="flightNumber" className="uppercase" aria-invalid={!!errors.flightNumber} {...register("flightNumber")} /></Field>
        <Field label="Aircraft" htmlFor="aircraftId" error={errors.aircraftId?.message}>
          <Select id="aircraftId" aria-invalid={!!errors.aircraftId} {...register("aircraftId")}><option value="">Select aircraft…</option>
            {aircraft.data?.filter((a) => a.status === "ACTIVE").map((a) => <option key={a.id} value={a.id}>{a.registration} · {a.model} ({a.capacity} seats)</option>)}</Select>
        </Field>
        <Field label="Origin" htmlFor="originCode" error={errors.originCode?.message}><Select id="originCode" {...register("originCode")}>{ap}</Select></Field>
        <Field label="Destination" htmlFor="destinationCode" error={errors.destinationCode?.message}><Select id="destinationCode" {...register("destinationCode")}>{ap}</Select></Field>
        <Field label="Departure (UTC)" htmlFor="departureTime" error={errors.departureTime?.message}><Input id="departureTime" type="datetime-local" {...register("departureTime")} /></Field>
        <Field label="Arrival (UTC)" htmlFor="arrivalTime" error={errors.arrivalTime?.message}><Input id="arrivalTime" type="datetime-local" {...register("arrivalTime")} /></Field>
        <Field label="Economy fare" htmlFor="economyFare" error={errors.economyFare?.message}><Input id="economyFare" type="number" min={1} step="0.01" {...register("economyFare")} /></Field>
        <Field label="Business fare" htmlFor="businessFare" error={errors.businessFare?.message}><Input id="businessFare" type="number" min={1} step="0.01" {...register("businessFare")} /></Field>
        <Field label="Gate" htmlFor="fgate" error={errors.gate?.message}><Input id="fgate" maxLength={6} {...register("gate")} /></Field>
        <Field label="Terminal" htmlFor="terminal" error={errors.terminal?.message}><Input id="terminal" maxLength={4} {...register("terminal")} /></Field>
        <button type="submit" className="hidden" />
      </form>
    </Dialog>
  );
}
