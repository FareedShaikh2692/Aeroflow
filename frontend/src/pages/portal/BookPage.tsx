import { useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { toast } from "sonner";
import { Check, CheckCircle2, CreditCard, Lock, Plane, User } from "lucide-react";
import { ApiError, api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { clean, passengersFormSchema, paymentSchema } from "@/lib/schemas";
import type { Booking, Cabin, Flight, SeatMap as SeatMapT } from "@/lib/types";
import { cn, formatDateTime, formatMoney, initials, newIdempotencyKey } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input, Select } from "@/components/ui/form";
import { Checkbox, ErrorState, Skeleton } from "@/components/ui/misc";
import { RouteLine } from "@/components/domain";
import { SeatMap } from "@/components/SeatMap";

const STEPS = ["Travellers", "Seats", "Payment", "Confirmation"];
type PaxForm = z.infer<typeof passengersFormSchema>;
type PayForm = z.infer<typeof paymentSchema>;

export default function BookPage() {
  const { flightId = "" } = useParams();
  const [params] = useSearchParams();
  const count = Math.min(9, Math.max(1, Number(params.get("passengers") ?? 1)));
  const [cabins, setCabins] = useState<Cabin[]>(() => Array(count).fill((params.get("cabin") ?? "ECONOMY") as Cabin));
  const [step, setStep] = useState(0);
  const [pax, setPax] = useState<PaxForm | null>(null);
  const [seats, setSeats] = useState<(string | null)[]>(Array(count).fill(null));
  const [active, setActive] = useState(0);
  const [booking, setBooking] = useState<Booking | null>(null);
  // one key per checkout attempt: retries of the same submit are deduplicated server-side
  const [idemKey] = useState(newIdempotencyKey);
  const { user } = useAuth();
  const qc = useQueryClient();

  const flight = useQuery({ queryKey: ["flight-public", flightId], queryFn: () => api<Flight>(`/api/flights/public/${flightId}`, { auth: false }) });
  const seatMap = useQuery({ queryKey: ["seatmap", flightId], queryFn: () => api<SeatMapT>(`/api/seat-maps/${flightId}`, { auth: false }), enabled: step >= 1 });

  const total = useMemo(() => cabins.reduce((s, c) => s + (flight.data ? (c === "BUSINESS" ? flight.data.businessFare : flight.data.economyFare) : 0), 0), [cabins, flight.data]);

  const create = useMutation({
    mutationFn: (payment: PayForm) => api<Booking>("/api/bookings/guest", {
      method: "POST", idempotencyKey: idemKey,
      body: {
        flightId, contactEmail: pax!.contactEmail, contactPhone: pax!.contactPhone || undefined,
        passengers: pax!.travellers.map((t, i) => ({ ...clean(t), cabin: cabins[i], seatNumber: seats[i] ?? undefined })),
        payment: { cardNumber: payment.cardNumber, expiry: payment.expiry, cvv: payment.cvv, cardHolder: payment.cardHolder },
      },
    }),
    onSuccess: (b) => {
      setBooking(b);
      setStep(3);
      qc.invalidateQueries({ queryKey: ["seatmap", flightId] });
      qc.invalidateQueries({ queryKey: ["my-bookings"] });
      toast.success(`Booking ${b.pnr} confirmed`);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === "SEAT_UNAVAILABLE") {
        toast.error(e.message + " - please choose again.");
        seatMap.refetch();
        setStep(1);
      } else toast.error(errorMessage(e));
    },
  });

  if (flight.isLoading) return <div className="mx-auto max-w-5xl p-6"><Skeleton className="h-40 w-full" /></div>;
  if (flight.isError || !flight.data) return <div className="mx-auto max-w-3xl p-6"><Card><ErrorState message={errorMessage(flight.error)} /></Card></div>;
  const f = flight.data;

  return (
    <div className="mx-auto grid max-w-7xl gap-6 px-4 py-8 sm:px-6 lg:grid-cols-[1fr_340px]">
      <div className="min-w-0 space-y-6">
        <ol className="flex flex-wrap gap-2" aria-label="Booking progress">
          {STEPS.map((s, i) => (
            <li key={s} aria-current={i === step ? "step" : undefined}
              className={cn("flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold", i === step ? "bg-navy-900 text-white" : i < step ? "bg-emerald-50 text-emerald-700" : "bg-navy-100 text-navy-500")}>
              {i < step ? <Check className="size-3.5" /> : <span>{i + 1}</span>}{s}
            </li>
          ))}
        </ol>

        {step === 0 && <TravellersStep count={count} cabins={cabins} setCabins={setCabins} defaults={pax}
          userEmail={user?.email} userName={user ? [user.firstName, user.lastName] : undefined}
          onNext={(v) => { setPax(v); setStep(1); }} />}

        {step === 1 && pax && (
          <Card>
            <CardHeader title="Choose seats" description="Select a traveller, then tap a seat. Skip to have seats assigned automatically." />
            <CardBody className="space-y-5">
              <div className="flex flex-wrap gap-2" role="tablist" aria-label="Travellers">
                {pax.travellers.map((t, i) => (
                  <button key={i} role="tab" aria-selected={active === i} onClick={() => setActive(i)}
                    className={cn("rounded-lg border px-3 py-2 text-left text-sm", active === i ? "border-navy-900 bg-navy-900 text-white" : "border-navy-200 bg-white")}>
                    <span className="block font-semibold">{t.firstName} {t.lastName}</span>
                    <span className={cn("text-xs", active === i ? "text-navy-200" : "text-navy-500")}>{cabins[i]} · {seats[i] ?? "No seat yet"}</span>
                  </button>
                ))}
              </div>
              {seatMap.isLoading ? <Skeleton className="h-96 w-full" /> : seatMap.data && (
                <SeatMap {...seatMap.data} cabin={cabins[active]!}
                  selected={Object.fromEntries(seats.map((s, i) => [s, initials(`${pax.travellers[i]!.firstName} ${pax.travellers[i]!.lastName}`)]).filter(([s]) => s)) as Record<string, string>}
                  onSelect={(seat) => {
                    if (seats.includes(seat) && seats[active] !== seat) return toast.info("That seat is held by another traveller in this booking");
                    setSeats((prev) => prev.map((s, i) => (i === active ? (s === seat ? null : seat) : s)));
                    if (active < count - 1 && seats[active] !== seat) setActive(active + 1);
                  }} />
              )}
              <div className="flex justify-between gap-3">
                <Button variant="outline" onClick={() => setStep(0)}>Back</Button>
                <Button onClick={() => setStep(2)}>Continue to payment</Button>
              </div>
            </CardBody>
          </Card>
        )}

        {step === 2 && <PaymentStep total={total} currency={f.currency} loading={create.isPending} onBack={() => setStep(1)} onPay={(v) => create.mutate(v)} />}

        {step === 3 && booking && <Confirmation booking={booking} />}
      </div>

      <aside className="lg:sticky lg:top-24 lg:self-start">
        <Card>
          <CardHeader title="Trip summary" description={`${f.flightNumber} · ${f.aircraft.model}`} />
          <CardBody className="space-y-4">
            <RouteLine compact origin={f.origin.code} destination={f.destination.code} departure={f.departureTime} arrival={f.arrivalTime}
              originTz={f.origin.timezone} destTz={f.destination.timezone} duration={f.durationMinutes} />
            <dl className="space-y-2 border-t border-navy-100 pt-4 text-sm">
              {cabins.map((c, i) => (
                <div key={i} className="flex justify-between"><dt className="text-navy-500">Traveller {i + 1} · {c === "BUSINESS" ? "Business" : "Economy"}{seats[i] ? ` · ${seats[i]}` : ""}</dt>
                  <dd className="font-semibold">{formatMoney(c === "BUSINESS" ? f.businessFare : f.economyFare, f.currency)}</dd></div>
              ))}
              <div className="flex justify-between border-t border-navy-100 pt-3 text-base"><dt className="font-bold">Total</dt><dd className="font-display font-extrabold" data-testid="booking-total">{formatMoney(total, f.currency)}</dd></div>
            </dl>
          </CardBody>
        </Card>
      </aside>
    </div>
  );
}

function TravellersStep({ count, cabins, setCabins, defaults, userEmail, userName, onNext }: {
  count: number; cabins: Cabin[]; setCabins: (c: Cabin[]) => void; defaults: PaxForm | null; userEmail?: string;
  userName?: string[]; onNext: (v: PaxForm) => void;
}) {
  const empty = { firstName: "", lastName: "", dateOfBirth: "", nationality: "", passportNumber: "", email: "" };
  const { register, control, handleSubmit, formState: { errors } } = useForm<PaxForm>({
    resolver: zodResolver(passengersFormSchema),
    defaultValues: defaults ?? {
      travellers: Array.from({ length: count }, (_, i) => i === 0 && userName ? { ...empty, firstName: userName[0]!, lastName: userName[1]!, email: userEmail ?? "" } : empty),
      contactEmail: userEmail ?? "", contactPhone: "",
    },
  });
  const { fields } = useFieldArray({ control, name: "travellers" });
  return (
    <form onSubmit={handleSubmit(onNext)} noValidate className="space-y-6" aria-label="Traveller details">
      {fields.map((fld, i) => {
        const e = errors.travellers?.[i];
        return (
          <Card key={fld.id}>
            <CardHeader title={<span className="flex items-center gap-2"><User className="size-4 text-accent-600" />Traveller {i + 1}</span>}
              action={<Select aria-label={`Cabin for traveller ${i + 1}`} className="w-36" value={cabins[i]} onChange={(ev) => setCabins(cabins.map((c, j) => (j === i ? (ev.target.value as Cabin) : c)))}>
                <option value="ECONOMY">Economy</option><option value="BUSINESS">Business</option></Select>} />
            <CardBody className="grid gap-4 sm:grid-cols-2">
              <Field label="First name" htmlFor={`t${i}-first`} error={e?.firstName?.message}><Input id={`t${i}-first`} aria-invalid={!!e?.firstName} {...register(`travellers.${i}.firstName`)} /></Field>
              <Field label="Last name" htmlFor={`t${i}-last`} error={e?.lastName?.message}><Input id={`t${i}-last`} aria-invalid={!!e?.lastName} {...register(`travellers.${i}.lastName`)} /></Field>
              <Field label="Date of birth" htmlFor={`t${i}-dob`} error={e?.dateOfBirth?.message}><Input id={`t${i}-dob`} type="date" {...register(`travellers.${i}.dateOfBirth`)} /></Field>
              <Field label="Nationality" htmlFor={`t${i}-nat`} error={e?.nationality?.message} hint="ISO code, e.g. AE"><Input id={`t${i}-nat`} maxLength={2} {...register(`travellers.${i}.nationality`)} /></Field>
              <Field label="Passport number" htmlFor={`t${i}-pp`} error={e?.passportNumber?.message}><Input id={`t${i}-pp`} {...register(`travellers.${i}.passportNumber`)} /></Field>
              <Field label="E-mail (optional)" htmlFor={`t${i}-email`} error={e?.email?.message}><Input id={`t${i}-email`} type="email" {...register(`travellers.${i}.email`)} /></Field>
            </CardBody>
          </Card>
        );
      })}
      <Card>
        <CardHeader title="Contact details" description="We'll send your confirmation and flight updates here." />
        <CardBody className="grid gap-4 sm:grid-cols-2">
          <Field label="E-mail" htmlFor="contactEmail" error={errors.contactEmail?.message}><Input id="contactEmail" type="email" autoComplete="email" aria-invalid={!!errors.contactEmail} {...register("contactEmail")} /></Field>
          <Field label="Mobile (for SMS updates)" htmlFor="contactPhone" error={errors.contactPhone?.message}><Input id="contactPhone" type="tel" autoComplete="tel" {...register("contactPhone")} /></Field>
        </CardBody>
      </Card>
      <div className="flex justify-end"><Button type="submit" size="lg">Continue to seats</Button></div>
    </form>
  );
}

function PaymentStep({ total, currency, loading, onBack, onPay }: { total: number; currency: string; loading: boolean; onBack: () => void; onPay: (v: PayForm) => void }) {
  const { register, handleSubmit, setValue, watch, formState: { errors } } = useForm<PayForm>({
    resolver: zodResolver(paymentSchema),
    defaultValues: { cardHolder: "", cardNumber: "", expiry: "", cvv: "", acceptTerms: false as unknown as true },
  });
  return (
    <form onSubmit={handleSubmit(onPay)} noValidate aria-label="Payment">
      <Card>
        <CardHeader title={<span className="flex items-center gap-2"><CreditCard className="size-4 text-accent-600" />Payment</span>}
          description="Payments run in test mode: your card is not charged and no ticket is issued. Test card: 4242 4242 4242 4242." />
        <CardBody className="grid gap-4 sm:grid-cols-2">
          <Field label="Name on card" htmlFor="cardHolder" error={errors.cardHolder?.message} className="sm:col-span-2"><Input id="cardHolder" autoComplete="cc-name" {...register("cardHolder")} /></Field>
          <Field label="Card number" htmlFor="cardNumber" error={errors.cardNumber?.message} className="sm:col-span-2"><Input id="cardNumber" inputMode="numeric" autoComplete="cc-number" placeholder="4242 4242 4242 4242" aria-invalid={!!errors.cardNumber} {...register("cardNumber")} /></Field>
          <Field label="Expiry" htmlFor="expiry" error={errors.expiry?.message}><Input id="expiry" placeholder="MM/YY" autoComplete="cc-exp" aria-invalid={!!errors.expiry} {...register("expiry")} /></Field>
          <Field label="CVV" htmlFor="cvv" error={errors.cvv?.message}><Input id="cvv" inputMode="numeric" autoComplete="cc-csc" type="password" aria-invalid={!!errors.cvv} {...register("cvv")} /></Field>
          <div className="sm:col-span-2">
            <Checkbox id="acceptTerms" checked={!!watch("acceptTerms")} onCheckedChange={(c) => setValue("acceptTerms", c as true, { shouldValidate: true })}
              label="I accept the fare rules and conditions of carriage." />
            {errors.acceptTerms && <p role="alert" className="mt-1 text-xs font-medium text-red-600">{errors.acceptTerms.message}</p>}
          </div>
        </CardBody>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-navy-100 px-5 py-4">
          <Button type="button" variant="outline" onClick={onBack}>Back</Button>
          <Button type="submit" size="lg" variant="accent" loading={loading}><Lock />Pay {formatMoney(total, currency)}</Button>
        </div>
      </Card>
    </form>
  );
}

function Confirmation({ booking }: { booking: Booking }) {
  return (
    <Card className="overflow-hidden">
      <div className="hero-sky px-6 py-8 text-white">
        <CheckCircle2 className="size-10 text-emerald-400" />
        <h2 className="mt-3 text-2xl font-extrabold">You're booked!</h2>
        <p className="mt-1 text-navy-200">Confirmation sent to {booking.contactEmail}</p>
        <p className="mt-5 text-xs uppercase tracking-widest text-navy-300">Booking reference</p>
        <p className="font-display text-4xl font-extrabold tracking-[0.2em]" data-testid="pnr">{booking.pnr}</p>
      </div>
      <CardBody className="space-y-4">
        <div className="flex flex-wrap items-center gap-3 text-sm"><Plane className="size-4 text-accent-600" /><strong>{booking.flightNumber}</strong> {booking.origin} → {booking.destination} · {formatDateTime(booking.departureTime)}</div>
        <ul className="divide-y divide-navy-100 rounded-lg border border-navy-100">
          {booking.passengers.map((p) => (
            <li key={p.id} className="flex justify-between px-4 py-3 text-sm"><span>{p.firstName} {p.lastName} · {p.cabin}</span><span className="font-bold">Seat {p.seatNumber}</span></li>
          ))}
        </ul>
        <p className="text-sm text-navy-500">Paid {formatMoney(booking.totalAmount, booking.currency)} with {booking.cardBrand} •••• {booking.cardLast4} · ref {booking.paymentReference}</p>
        <div className="flex flex-wrap gap-2">
          <Button asChild><Link to="/check-in">Go to check-in</Link></Button>
          <Button asChild variant="outline"><Link to="/manage">Manage booking</Link></Button>
        </div>
      </CardBody>
    </Card>
  );
}
