import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { ArrowLeftRight, CalendarCheck2, Plane, Search, ShieldCheck, Smartphone } from "lucide-react";
import { useAirports } from "@/lib/queries";
import { searchSchema } from "@/lib/schemas";
import { todayIso } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Field, Input, Select } from "@/components/ui/form";

type SearchValues = z.infer<typeof searchSchema>;

export function SearchForm({ initial }: { initial?: Partial<SearchValues> }) {
  const navigate = useNavigate();
  const airports = useAirports();
  const { register, handleSubmit, setValue, getValues, formState: { errors } } = useForm<SearchValues>({
    resolver: zodResolver(searchSchema),
    defaultValues: { origin: "DXB", destination: "LHR", date: todayIso(1), passengers: 1, cabin: "ECONOMY", ...initial },
  });
  // options arrive asynchronously: re-apply the current values so the <select>s show them
  useEffect(() => {
    if (airports.data) {
      setValue("origin", getValues("origin"));
      setValue("destination", getValues("destination"));
    }
  }, [airports.data, getValues, setValue]);
  const submit = handleSubmit((v) => navigate(`/flights?${new URLSearchParams({ ...v, passengers: String(v.passengers) })}`));
  const swap = () => {
    const { origin, destination } = getValues();
    setValue("origin", destination);
    setValue("destination", origin);
  };
  const options = airports.data?.map((a) => <option key={a.code} value={a.code}>{a.city} ({a.code})</option>);
  return (
    <form onSubmit={submit} noValidate aria-label="Search flights" className="grid gap-4 rounded-2xl bg-white p-5 shadow-lift sm:grid-cols-2 lg:grid-cols-[1fr_auto_1fr_1fr_0.7fr_0.9fr_auto] lg:items-end">
      <Field label="From" htmlFor="origin" error={errors.origin?.message}>
        <Select id="origin" {...register("origin")} disabled={airports.isLoading}>{options ?? <option>DXB</option>}</Select>
      </Field>
      <Button type="button" variant="ghost" size="icon" onClick={swap} aria-label="Swap origin and destination" className="hidden lg:inline-flex mb-0.5"><ArrowLeftRight /></Button>
      <Field label="To" htmlFor="destination" error={errors.destination?.message}>
        <Select id="destination" {...register("destination")} disabled={airports.isLoading}>{options ?? <option>LHR</option>}</Select>
      </Field>
      <Field label="Departure" htmlFor="date" error={errors.date?.message}>
        <Input id="date" type="date" min={todayIso()} {...register("date")} />
      </Field>
      <Field label="Travellers" htmlFor="passengers" error={errors.passengers?.message}>
        <Select id="passengers" {...register("passengers")}>{[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => <option key={n} value={n}>{n}</option>)}</Select>
      </Field>
      <Field label="Cabin" htmlFor="cabin"><Select id="cabin" {...register("cabin")}><option value="ECONOMY">Economy</option><option value="BUSINESS">Business</option></Select></Field>
      <Button type="submit" size="lg" variant="accent" className="sm:col-span-2 lg:col-span-1"><Search />Search</Button>
    </form>
  );
}

export function HomePage() {
  return (
    <>
      <section className="hero-sky relative overflow-hidden">
        <div className="mx-auto max-w-7xl px-4 pb-24 pt-16 sm:px-6 sm:pt-24">
          <p className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3 py-1 text-xs font-semibold uppercase tracking-widest text-accent-400">
            <Plane className="size-3.5" />Dubai hub · 14 destinations
          </p>
          <h1 className="mt-5 max-w-3xl text-4xl font-extrabold leading-[1.08] text-white sm:text-6xl">Fly further, <span className="text-gold-400">beautifully</span> connected.</h1>
          <p className="mt-5 max-w-xl text-base text-navy-200 sm:text-lg">Search, book, choose your seat and check in online - all in one seamless journey.</p>
          <div className="mt-10"><SearchForm /></div>
        </div>
      </section>
      <section className="mx-auto grid max-w-7xl gap-5 px-4 py-14 sm:grid-cols-3 sm:px-6">
        {[
          { icon: CalendarCheck2, title: "Choose your seat", text: "Pick the exact seat you want on an interactive cabin map." },
          { icon: Smartphone, title: "Online check-in", text: "Check in 48 hours before departure and download your boarding pass." },
          { icon: ShieldCheck, title: "Book with confidence", text: "Secure, idempotent booking - you will never be charged twice." },
        ].map((f) => (
          <div key={f.title} className="rounded-2xl border border-navy-100 bg-white p-6 shadow-card">
            <f.icon className="size-6 text-accent-600" />
            <h2 className="mt-4 text-lg font-bold">{f.title}</h2>
            <p className="mt-1 text-sm text-navy-500">{f.text}</p>
          </div>
        ))}
      </section>
    </>
  );
}
