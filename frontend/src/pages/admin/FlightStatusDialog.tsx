import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, errorMessage } from "@/lib/api";
import type { Flight, FlightStatus } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";

const NEXT: Record<FlightStatus, FlightStatus[]> = {
  SCHEDULED: ["DELAYED", "BOARDING", "CANCELLED"], DELAYED: ["DELAYED", "SCHEDULED", "BOARDING", "CANCELLED"],
  BOARDING: ["DEPARTED", "DELAYED", "CANCELLED"], DEPARTED: ["ARRIVED"], ARRIVED: [], CANCELLED: [],
};

export function allowedTransitions(s: FlightStatus) { return NEXT[s]; }

export function FlightStatusDialog({ flight, onClose }: { flight: Flight | null; onClose: () => void }) {
  const qc = useQueryClient();
  const options = flight ? NEXT[flight.status] : [];
  const [status, setStatus] = useState<FlightStatus>("DELAYED");
  const [delay, setDelay] = useState("30");
  const [reason, setReason] = useState("");
  const [gate, setGate] = useState("");
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (flight) { setStatus(options[0] ?? "DELAYED"); setDelay(String(flight.delayMinutes || 30)); setReason(""); setGate(flight.gate ?? ""); setError(null); } }, [flight]); // eslint-disable-line react-hooks/exhaustive-deps

  const m = useMutation({
    mutationFn: () => api<Flight>(`/api/flights/${flight!.id}/status`, { method: "PATCH",
      body: { status, delayMinutes: status === "DELAYED" ? Number(delay) : undefined, reason: reason || undefined, gate: gate || undefined } }),
    onSuccess: (f) => {
      toast.success(`${f.flightNumber} is now ${f.status.toLowerCase()} - affected passengers are being notified`);
      qc.invalidateQueries({ queryKey: ["flights"] });
      qc.invalidateQueries({ queryKey: ["flight", f.id] });
      qc.invalidateQueries({ queryKey: ["ops-board"] });
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const submit = () => {
    if (status === "DELAYED" && !(Number(delay) >= 1 && Number(delay) <= 1440)) return setError("Delay must be 1–1440 minutes");
    if (status === "CANCELLED" && !reason.trim()) return setError("A cancellation reason is required");
    m.mutate();
  };
  return (
    <Dialog open={!!flight} onOpenChange={(o) => !o && onClose()} title={`Update ${flight?.flightNumber ?? ""} status`}
      description={flight ? `${flight.origin.code} → ${flight.destination.code} · currently ${flight.status.toLowerCase()}` : undefined}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button variant={status === "CANCELLED" ? "danger" : "primary"} loading={m.isPending} onClick={submit} disabled={!options.length}>Apply change</Button></>}>
      {options.length === 0 ? <p className="text-sm text-navy-600">This flight is {flight?.status.toLowerCase()} and can no longer change status.</p> : (
        <div className="space-y-4">
          {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <Field label="New status" htmlFor="st"><Select id="st" value={status} onChange={(e) => setStatus(e.target.value as FlightStatus)}>{options.map((o) => <option key={o} value={o}>{o}</option>)}</Select></Field>
          {status === "DELAYED" && <Field label="Delay (minutes)" htmlFor="delay"><Input id="delay" type="number" min={1} max={1440} value={delay} onChange={(e) => setDelay(e.target.value)} /></Field>}
          {(status === "BOARDING" || status === "DELAYED") && <Field label="Gate" htmlFor="gate"><Input id="gate" value={gate} maxLength={6} onChange={(e) => setGate(e.target.value.toUpperCase())} /></Field>}
          <Field label={status === "CANCELLED" ? "Reason (required)" : "Reason (shared with passengers)"} htmlFor="reason"><Textarea id="reason" maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
          {status === "CANCELLED" && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">All confirmed bookings will be cancelled and fully refunded automatically.</p>}
        </div>
      )}
    </Dialog>
  );
}
