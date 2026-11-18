import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { CheckCircle2, Download, Luggage, PlaneTakeoff } from "lucide-react";
import { api, errorMessage } from "@/lib/api";
import type { BoardingPass, CheckinView, SeatMap as SeatMapT } from "@/lib/types";
import { cn, downloadBlob, formatDateTime, formatTime } from "@/lib/utils";
import { Button } from "./ui/button";
import { Card, CardBody, CardHeader } from "./ui/card";
import { Checkbox, ErrorState, Skeleton } from "./ui/misc";
import { Badge } from "./ui/badge";
import { SeatMap } from "./SeatMap";
import { PnrForm, type PnrValues } from "./PnrForm";

/** Retrieve → verify → seats → declaration → boarding passes. Shared by passenger portal and agent desk. */
export function CheckinFlow({ agent }: { agent?: boolean }) {
  const [creds, setCreds] = useState<PnrValues | null>(null);
  const [view, setView] = useState<CheckinView | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [declared, setDeclared] = useState(false);
  const [seatFor, setSeatFor] = useState<string | null>(null);

  const retrieve = useMutation({
    mutationFn: (v: PnrValues) => api<CheckinView>("/api/checkin/retrieve", { method: "POST", body: v }),
    onSuccess: (v, vars) => {
      setCreds(vars);
      setView(v);
      setSelected(v.booking.passengers.filter((p) => !p.checkedIn).map((p) => p.id));
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const seatMap = useQuery({
    queryKey: ["seatmap", view?.booking.flightId], enabled: !!view && !!seatFor,
    queryFn: () => api<SeatMapT>(`/api/seat-maps/${view!.booking.flightId}`, { auth: false }),
  });
  const changeSeat = useMutation({
    mutationFn: (seat: string) => api("/api/checkin/seat", { method: "PUT", body: { ...creds, passengerId: seatFor, seatNumber: seat } }),
    onSuccess: async () => { toast.success("Seat updated"); setSeatFor(null); retrieve.mutate(creds!); },
    onError: (e) => { toast.error(errorMessage(e)); seatMap.refetch(); },
  });
  const complete = useMutation({
    mutationFn: () => api<CheckinView>("/api/checkin/complete", { method: "POST", body: { ...creds, passengerIds: selected, acceptDeclaration: declared } }),
    onSuccess: (v) => { setView(v); setSelected([]); toast.success("Check-in complete - boarding passes issued"); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const download = async (bp: BoardingPass) => {
    try {
      const blob = await api<Blob>("/api/checkin/boarding-pass", { method: "POST", raw: true, body: { ...creds, boardingPassNumber: bp.boardingPassNumber } });
      downloadBlob(blob, `boarding-pass-${bp.boardingPassNumber}.pdf`);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  if (!view) {
    return (
      <Card>
        <CardHeader title={agent ? "Agent check-in" : "Online check-in"} description="Opens 48 hours and closes 60 minutes before departure." />
        <CardBody><PnrForm onSubmit={(v) => retrieve.mutate(v)} loading={retrieve.isPending} label="Start check-in" /></CardBody>
      </Card>
    );
  }

  const b = view.booking;
  const pending = b.passengers.filter((p) => !p.checkedIn);
  const canCheckIn = (view.eligibility.open || agent) && b.status === "CONFIRMED";
  const seatPassenger = b.passengers.find((p) => p.id === seatFor);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title={<span className="flex items-center gap-2"><PlaneTakeoff className="size-4 text-accent-600" />{b.flightNumber} · {b.origin} → {b.destination}</span>}
          description={`Departs ${formatDateTime(b.departureTime)} · Booking ${b.pnr}`}
          action={<Button variant="ghost" size="sm" onClick={() => { setView(null); setCreds(null); }}>Find another booking</Button>} />
        <CardBody className="space-y-4">
          {!view.eligibility.open && (
            <div role="status" className={cn("rounded-lg border px-4 py-3 text-sm", agent ? "border-amber-200 bg-amber-50 text-amber-800" : "border-navy-200 bg-navy-50 text-navy-700")}>
              {view.eligibility.reason}. {agent ? "As an agent you may override the online window." : `Opens ${formatDateTime(view.eligibility.opensAt)}.`}
            </div>
          )}
          <ul className="divide-y divide-navy-100 rounded-xl border border-navy-100">
            {b.passengers.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                <div className="flex items-center gap-3">
                  {!p.checkedIn && canCheckIn ? (
                    <Checkbox id={`pax-${p.id}`} checked={selected.includes(p.id)} label={<span className="font-semibold">{p.firstName} {p.lastName}</span>}
                      onCheckedChange={(c) => setSelected((s) => (c ? [...s, p.id] : s.filter((x) => x !== p.id)))} />
                  ) : <span className="font-semibold">{p.firstName} {p.lastName}</span>}
                  <Badge tone={p.cabin === "BUSINESS" ? "gold" : "blue"}>{p.cabin}</Badge>
                  {p.checkedIn && <Badge tone="green" dot>Checked in</Badge>}
                </div>
                <div className="flex items-center gap-3 text-sm">
                  Seat <strong data-testid={`seat-of-${p.firstName}`}>{p.seatNumber}</strong>
                  {!p.checkedIn && canCheckIn && <Button size="sm" variant="outline" onClick={() => setSeatFor(p.id)}>Change seat</Button>}
                </div>
              </li>
            ))}
          </ul>
          {seatPassenger && (
            <div className="rounded-xl border border-accent-100 bg-accent-50/40 p-4">
              <div className="mb-3 flex items-center justify-between"><p className="text-sm font-semibold">Choose a new seat for {seatPassenger.firstName}</p><Button size="sm" variant="ghost" onClick={() => setSeatFor(null)}>Cancel</Button></div>
              {seatMap.isLoading ? <Skeleton className="h-80 w-full" /> : seatMap.isError ? <ErrorState message={errorMessage(seatMap.error)} /> : seatMap.data && (
                <SeatMap {...seatMap.data} cabin={seatPassenger.cabin} selected={{ [seatPassenger.seatNumber]: "•" }} onSelect={(s) => changeSeat.mutate(s)} readOnly={changeSeat.isPending} />
              )}
            </div>
          )}
          {pending.length > 0 && canCheckIn && (
            <div className="space-y-4 rounded-xl border border-navy-100 bg-navy-50/50 p-4">
              <div className="flex gap-3 text-sm text-navy-700"><Luggage className="mt-0.5 size-5 shrink-0 text-navy-500" />
                <p>Dangerous goods: I confirm that no traveller is carrying prohibited items such as explosives, flammable liquids, compressed gases or lithium batteries in checked baggage.</p></div>
              <Checkbox id="declaration" checked={declared} onCheckedChange={setDeclared} label="I accept the dangerous goods declaration" />
              <Button size="lg" disabled={!declared || selected.length === 0} loading={complete.isPending} onClick={() => complete.mutate()}>
                <CheckCircle2 />Check in {selected.length} traveller{selected.length === 1 ? "" : "s"}
              </Button>
            </div>
          )}
        </CardBody>
      </Card>

      {view.boardingPasses.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {view.boardingPasses.map((bp) => (
            <Card key={bp.id} className="overflow-hidden" data-testid="boarding-pass">
              <div className="flex items-center justify-between bg-navy-900 px-5 py-3 text-white">
                <span className="font-display font-extrabold">AeroFlow</span><span className="text-xs font-semibold uppercase tracking-widest">{bp.cabin} · Group {bp.boardingGroup}</span>
              </div>
              <div className="grid grid-cols-3 gap-3 p-5 text-sm">
                <div className="col-span-3 font-display text-3xl font-extrabold">{bp.origin} <span className="text-accent-500">✈</span> {bp.destination}</div>
                <Info label="Passenger" value={bp.passengerName} span />
                <Info label="Flight" value={bp.flightNumber} />
                <Info label="Seat" value={bp.seatNumber} />
                <Info label="Boarding" value={formatTime(bp.boardingTime)} />
                <Info label="Gate" value={bp.gate ?? "TBA"} />
                <Info label="Seq" value={String(bp.sequenceNumber).padStart(3, "0")} />
                <Info label="Departs" value={formatTime(bp.departureTime)} />
              </div>
              <div className="border-t border-dashed border-navy-200 px-5 py-3">
                <Button variant="outline" size="sm" onClick={() => download(bp)}><Download />Download PDF</Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function Info({ label, value, span }: { label: string; value: string; span?: boolean }) {
  return <div className={span ? "col-span-2" : ""}><p className="text-[10px] font-bold uppercase tracking-wider text-navy-400">{label}</p><p className="font-bold text-navy-900">{value}</p></div>;
}
