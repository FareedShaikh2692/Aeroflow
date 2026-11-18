import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { useNavigate } from "react-router-dom";
import { api, errorMessage, API_BASE } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { PASSWORD_RULE } from "@/lib/schemas";
import { ROLE_LABELS } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/domain";

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [cur, setCur] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const m = useMutation({
    mutationFn: () => api("/api/auth/change-password", { method: "POST", body: { currentPassword: cur, newPassword: next } }),
    onSuccess: async () => { toast.success("Password changed. Please sign in again."); await logout(); navigate("/login"); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const nextErr = next && !PASSWORD_RULE.test(next) ? "10+ chars with upper, lower, number, symbol" : undefined;
  const confirmErr = confirm && confirm !== next ? "Passwords do not match" : undefined;
  return (
    <>
      <PageHeader title="Settings" description="Your profile, security and environment." crumbs={[{ label: "Console", to: "/admin" }, { label: "Settings" }]} />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card><CardHeader title="Profile" /><CardBody className="space-y-3 text-sm">
          <p><span className="text-navy-500">Name:</span> <strong>{user?.firstName} {user?.lastName}</strong></p>
          <p><span className="text-navy-500">E-mail:</span> <strong>{user?.email}</strong></p>
          <p className="flex flex-wrap gap-1"><span className="text-navy-500">Roles:</span>{user?.roles.map((r) => <Badge key={r} tone="blue">{ROLE_LABELS[r]}</Badge>)}</p>
          <p><span className="text-navy-500">API endpoint:</span> <code className="rounded bg-navy-50 px-1.5 py-0.5 text-xs">{API_BASE || window.location.origin}</code></p>
        </CardBody></Card>
        <Card><CardHeader title="Change password" description="Signs you out of all devices." />
          <CardBody>
            <form className="space-y-4" noValidate onSubmit={(e) => { e.preventDefault(); if (!nextErr && !confirmErr && cur && next) m.mutate(); }} aria-label="Change password">
              <Field label="Current password" htmlFor="cur"><Input id="cur" type="password" autoComplete="current-password" value={cur} onChange={(e) => setCur(e.target.value)} /></Field>
              <Field label="New password" htmlFor="new" error={nextErr}><Input id="new" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} /></Field>
              <Field label="Confirm new password" htmlFor="conf" error={confirmErr}><Input id="conf" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></Field>
              <Button type="submit" loading={m.isPending} disabled={!cur || !next || !!nextErr || !!confirmErr}>Update password</Button>
            </form>
          </CardBody></Card>
      </div>
    </>
  );
}
