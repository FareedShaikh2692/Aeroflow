import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { toast } from "sonner";
import { KeyRound, ShieldCheck } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { errorMessage } from "@/lib/api";
import { loginSchema, registerSchema } from "@/lib/schemas";
import { STAFF_ROLES } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Card } from "@/components/ui/card";

const DEMO = [
  ["superadmin@aeroflow.dev", "Super admin"], ["admin@aeroflow.dev", "Admin"], ["ops@aeroflow.dev", "Operations"],
  ["agent@aeroflow.dev", "Flight agent"], ["checkin@aeroflow.dev", "Check-in agent"], ["passenger@aeroflow.dev", "Passenger"],
] as const;

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="hero-sky flex min-h-[calc(100vh-8rem)] items-center justify-center px-4 py-12">
      <Card className="w-full max-w-md p-7 sm:p-8">
        <h1 className="text-2xl font-extrabold">{title}</h1>
        <p className="mt-1 text-sm text-navy-500">{subtitle}</p>
        <div className="mt-6">{children}</div>
      </Card>
    </div>
  );
}

export function LoginForm({ onSuccess }: { onSuccess: (isStaff: boolean) => void }) {
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });
  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } = form;

  const submit = handleSubmit(async (v) => {
    setError(null);
    try {
      const u = await login(v.email, v.password);
      toast.success(`Welcome back, ${u.firstName}`);
      onSuccess(u.roles.some((r) => STAFF_ROLES.includes(r)));
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  return (
    <form onSubmit={submit} noValidate className="space-y-4" aria-label="Sign in">
      {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
      <Field label="E-mail" htmlFor="email" error={errors.email?.message}>
        <Input id="email" type="email" autoComplete="username" aria-invalid={!!errors.email} {...register("email")} />
      </Field>
      <Field label="Password" htmlFor="password" error={errors.password?.message}>
        <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!errors.password} {...register("password")} />
      </Field>
      <Button type="submit" className="w-full" size="lg" loading={isSubmitting}><KeyRound />Sign in</Button>
      <details className="rounded-lg border border-navy-100 bg-navy-50/60 p-3 text-sm">
        <summary className="cursor-pointer font-semibold text-navy-700">Demo accounts (password: Aeroflow@2026)</summary>
        <div className="mt-3 grid grid-cols-2 gap-2">
          {DEMO.map(([email, label]) => (
            <button key={email} type="button" className="rounded-md border border-navy-200 bg-white px-2 py-1.5 text-left text-xs hover:border-accent-500"
              onClick={() => { setValue("email", email); setValue("password", "Aeroflow@2026"); }}>
              <span className="block font-semibold text-navy-800">{label}</span><span className="text-navy-500">{email}</span>
            </button>
          ))}
        </div>
      </details>
    </form>
  );
}

export function LoginPage() {
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from;
  return (
    <AuthShell title="Sign in to AeroFlow" subtitle="Passengers and airline staff use the same secure sign-in.">
      <LoginForm onSuccess={(staff) => navigate(from ?? (staff ? "/admin" : "/my-trips"), { replace: true })} />
      <p className="mt-6 text-center text-sm text-navy-500">New to AeroFlow? <Link to="/register" className="font-semibold text-accent-600">Create an account</Link></p>
    </AuthShell>
  );
}

export function RegisterPage() {
  const { register: signUp } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
    defaultValues: { firstName: "", lastName: "", email: "", phone: "", password: "", confirm: "" },
  });
  const submit = handleSubmit(async ({ confirm: _c, phone, ...v }) => {
    setError(null);
    try {
      await signUp({ ...v, phone: phone || undefined });
      toast.success("Account created - welcome aboard!");
      navigate("/my-trips");
    } catch (e) {
      setError(errorMessage(e));
    }
  });
  return (
    <AuthShell title="Create your account" subtitle="Manage trips, check in faster and earn loyalty points.">
      <form onSubmit={submit} noValidate className="space-y-4" aria-label="Register">
        {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <Field label="First name" htmlFor="firstName" error={errors.firstName?.message}><Input id="firstName" autoComplete="given-name" aria-invalid={!!errors.firstName} {...register("firstName")} /></Field>
          <Field label="Last name" htmlFor="lastName" error={errors.lastName?.message}><Input id="lastName" autoComplete="family-name" aria-invalid={!!errors.lastName} {...register("lastName")} /></Field>
        </div>
        <Field label="E-mail" htmlFor="reg-email" error={errors.email?.message}><Input id="reg-email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register("email")} /></Field>
        <Field label="Mobile (optional)" htmlFor="phone" error={errors.phone?.message}><Input id="phone" type="tel" autoComplete="tel" placeholder="+971 50 000 0000" {...register("phone")} /></Field>
        <Field label="Password" htmlFor="reg-password" error={errors.password?.message} hint="10+ characters with upper, lower, number and symbol">
          <Input id="reg-password" type="password" autoComplete="new-password" aria-invalid={!!errors.password} {...register("password")} />
        </Field>
        <Field label="Confirm password" htmlFor="confirm" error={errors.confirm?.message}><Input id="confirm" type="password" autoComplete="new-password" aria-invalid={!!errors.confirm} {...register("confirm")} /></Field>
        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}><ShieldCheck />Create account</Button>
      </form>
      <p className="mt-6 text-center text-sm text-navy-500">Already a member? <Link to="/login" className="font-semibold text-accent-600">Sign in</Link></p>
    </AuthShell>
  );
}
