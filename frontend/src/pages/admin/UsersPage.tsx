import { useEffect, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { toast } from "sonner";
import { KeyRound, Pencil, Plus, Search } from "lucide-react";
import { ApiError, api, errorMessage, qs } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { PASSWORD_RULE, userSchema } from "@/lib/schemas";
import { ROLE_LABELS, type Page, type Role, type User } from "@/lib/types";
import { formatDateTime } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { Checkbox, EmptyState, ErrorState, Pagination, Table, TableSkeleton, Td, Th } from "@/components/ui/misc";
import { PageHeader } from "@/components/domain";

type Values = z.infer<typeof userSchema>;
const ROLES = Object.keys(ROLE_LABELS) as Role[];

function UserDialog({ open, user, onClose }: { open: boolean; user?: User | null; onClose: () => void }) {
  const qc = useQueryClient();
  const { hasRole } = useAuth();
  const { register, handleSubmit, reset, watch, setValue, setError, formState: { errors } } = useForm<Values>({ resolver: zodResolver(userSchema) });
  useEffect(() => {
    if (open) reset({ firstName: user?.firstName ?? "", lastName: user?.lastName ?? "", email: user?.email ?? "", phone: user?.phone ?? "", password: "", roles: user?.roles ?? ["FLIGHT_AGENT"], enabled: user?.enabled ?? true });
  }, [open, user, reset]);
  const roles = watch("roles") ?? [];
  const m = useMutation({
    mutationFn: (v: Values) => {
      if (!user && !v.password) throw new ApiError(400, "VALIDATION", "A password is required for new users");
      const body = { firstName: v.firstName, lastName: v.lastName, phone: v.phone || undefined, roles: v.roles, enabled: v.enabled };
      return user ? api<User>(`/api/users/${user.id}`, { method: "PUT", body }) : api<User>("/api/users", { method: "POST", body: { ...body, email: v.email, password: v.password } });
    },
    onSuccess: (u) => { toast.success(`${u.email} saved`); qc.invalidateQueries({ queryKey: ["users"] }); onClose(); },
    onError: (e) => { if (e instanceof ApiError) e.fieldErrors.forEach((fe) => setError(fe.field as keyof Values, { message: fe.message })); toast.error(errorMessage(e)); },
  });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} wide title={user ? `Edit ${user.email}` : "Create user"}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button loading={m.isPending} onClick={handleSubmit((v) => m.mutate(v))}>Save user</Button></>}>
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit((v) => m.mutate(v))} aria-label="User form">
        <Field label="First name" htmlFor="u-first" error={errors.firstName?.message}><Input id="u-first" {...register("firstName")} /></Field>
        <Field label="Last name" htmlFor="u-last" error={errors.lastName?.message}><Input id="u-last" {...register("lastName")} /></Field>
        <Field label="E-mail" htmlFor="u-email" error={errors.email?.message}><Input id="u-email" type="email" disabled={!!user} {...register("email")} /></Field>
        <Field label="Phone" htmlFor="u-phone" error={errors.phone?.message}><Input id="u-phone" {...register("phone")} /></Field>
        {!user && <Field label="Initial password" htmlFor="u-pw" className="sm:col-span-2" error={errors.password?.message} hint="10+ chars with upper, lower, number, symbol"><Input id="u-pw" type="password" autoComplete="new-password" {...register("password")} /></Field>}
        <fieldset className="sm:col-span-2"><legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-navy-600">Roles</legend>
          <div className="grid gap-2 sm:grid-cols-3">{ROLES.map((r) => (
            <Checkbox key={r} id={`role-${r}`} label={ROLE_LABELS[r]} checked={roles.includes(r)} disabled={r === "SUPER_ADMIN" && !hasRole("SUPER_ADMIN")}
              onCheckedChange={(c) => setValue("roles", c ? [...roles, r] : roles.filter((x) => x !== r), { shouldValidate: true })} />))}</div>
          {errors.roles && <p role="alert" className="mt-1 text-xs text-red-600">{errors.roles.message}</p>}
        </fieldset>
        <div className="sm:col-span-2"><Checkbox id="u-enabled" label="Account enabled" checked={watch("enabled")} onCheckedChange={(c) => setValue("enabled", c)} /></div>
        <button type="submit" className="hidden" />
      </form>
    </Dialog>
  );
}

function ResetDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const [pw, setPw] = useState("");
  const m = useMutation({
    mutationFn: () => api(`/api/users/${user!.id}/reset-password`, { method: "POST", body: { newPassword: pw } }),
    onSuccess: () => { toast.success("Password reset - all sessions revoked"); setPw(""); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const valid = PASSWORD_RULE.test(pw);
  return (
    <Dialog open={!!user} onOpenChange={(o) => !o && onClose()} title={`Reset password for ${user?.email ?? ""}`} description="The user is signed out on every device."
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={!valid} loading={m.isPending} onClick={() => m.mutate()}>Reset password</Button></>}>
      <Field label="New password" htmlFor="reset-pw" error={pw && !valid ? "10+ chars with upper, lower, number, symbol" : undefined}><Input id="reset-pw" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
    </Dialog>
  );
}

export default function UsersPage() {
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<{ open: boolean; user?: User | null }>({ open: false });
  const [reset, setReset] = useState<User | null>(null);
  const list = useQuery({ queryKey: ["users", search, role, page], queryFn: () => api<Page<User>>(`/api/users${qs({ search, role, page, size: 20 })}`), placeholderData: keepPreviousData });
  return (
    <>
      <PageHeader title="Users & roles" description="Manage staff accounts and role-based access." crumbs={[{ label: "Console", to: "/admin" }, { label: "Users" }]}
        actions={<Button onClick={() => setDialog({ open: true, user: null })}><Plus />Create user</Button>} />
      <Card>
        <div className="grid gap-3 border-b border-navy-100 p-4 sm:grid-cols-[1fr_240px]">
          <div className="relative"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-navy-400" /><Input aria-label="Search users" placeholder="Name or e-mail" className="pl-9" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} /></div>
          <Select aria-label="Role filter" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}><option value="">All roles</option>{ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}</Select>
        </div>
        {list.isLoading ? <TableSkeleton /> : list.isError ? <ErrorState message={(list.error as Error).message} onRetry={() => list.refetch()} />
          : list.data!.content.length === 0 ? <EmptyState title="No users found" /> : (
          <Table><thead><tr><Th>User</Th><Th>Roles</Th><Th>Status</Th><Th>Last sign-in</Th><Th className="text-right">Actions</Th></tr></thead>
            <tbody>{list.data!.content.map((u) => (
              <tr key={u.id}><Td><p className="font-semibold">{u.firstName} {u.lastName}</p><p className="text-xs text-navy-500">{u.email}</p></Td>
                <Td className="space-x-1">{u.roles.map((r) => <Badge key={r} tone={r === "SUPER_ADMIN" || r === "ADMIN" ? "violet" : r === "PASSENGER" ? "neutral" : "blue"}>{ROLE_LABELS[r]}</Badge>)}</Td>
                <Td>{!u.enabled ? <Badge tone="red">Disabled</Badge> : u.lockedUntil && new Date(u.lockedUntil) > new Date() ? <Badge tone="amber">Locked</Badge> : <Badge tone="green">Active</Badge>}</Td>
                <Td className="text-xs text-navy-500">{formatDateTime(u.lastLoginAt)}</Td>
                <Td className="space-x-1 text-right"><Button size="sm" variant="ghost" onClick={() => setDialog({ open: true, user: u })} aria-label={`Edit ${u.email}`}><Pencil /></Button>
                  <Button size="sm" variant="ghost" onClick={() => setReset(u)} aria-label={`Reset password for ${u.email}`}><KeyRound /></Button></Td></tr>))}</tbody></Table>)}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />}
      </Card>
      <UserDialog open={dialog.open} user={dialog.user} onClose={() => setDialog({ open: false })} />
      <ResetDialog user={reset} onClose={() => setReset(null)} />
    </>
  );
}
