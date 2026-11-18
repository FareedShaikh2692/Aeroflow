import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { Search } from "lucide-react";
import { pnrSchema } from "@/lib/schemas";
import { Button } from "./ui/button";
import { Field, Input } from "./ui/form";

export type PnrValues = z.infer<typeof pnrSchema>;

export function PnrForm({ onSubmit, loading, label = "Find booking" }: { onSubmit: (v: PnrValues) => void; loading?: boolean; label?: string }) {
  const { register, handleSubmit, formState: { errors } } = useForm<PnrValues>({ resolver: zodResolver(pnrSchema), defaultValues: { pnr: "", lastName: "" } });
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end" aria-label="Find booking">
      <Field label="Booking reference" htmlFor="pnr" error={errors.pnr?.message}>
        <Input id="pnr" placeholder="e.g. K7Q2MX" className="uppercase tracking-widest" maxLength={6} aria-invalid={!!errors.pnr} {...register("pnr")} />
      </Field>
      <Field label="Passenger last name" htmlFor="lastName" error={errors.lastName?.message}>
        <Input id="lastName" autoComplete="family-name" aria-invalid={!!errors.lastName} {...register("lastName")} />
      </Field>
      <Button type="submit" size="lg" loading={loading}><Search />{label}</Button>
    </form>
  );
}
