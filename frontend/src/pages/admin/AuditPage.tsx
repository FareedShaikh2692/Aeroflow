import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { api, qs } from "@/lib/api";
import type { AuditEntry, Page } from "@/lib/types";
import { formatDateTime } from "@/lib/utils";
import { Card } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorState, Pagination, Table, TableSkeleton, Td, Th } from "@/components/ui/misc";
import { PageHeader } from "@/components/domain";

export default function AuditPage() {
  const [f, setF] = useState({ q: "", action: "", from: "", to: "" });
  const [page, setPage] = useState(0);
  const actions = useQuery({ queryKey: ["audit-actions"], queryFn: () => api<string[]>("/api/audit/actions") });
  const list = useQuery({ queryKey: ["audit", f, page], queryFn: () => api<Page<AuditEntry>>(`/api/audit${qs({ ...f, page, size: 25 })}`), placeholderData: keepPreviousData });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setF({ ...f, [k]: e.target.value }); setPage(0); };
  return (
    <>
      <PageHeader title="Audit log" description="Immutable record of administrative actions across all services." crumbs={[{ label: "Console", to: "/admin" }, { label: "Audit log" }]} />
      <Card>
        <div className="grid gap-3 border-b border-navy-100 p-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="relative"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-navy-400" /><Input aria-label="Search audit log" placeholder="Actor, entity or details" className="pl-9" value={f.q} onChange={set("q")} /></div>
          <Select aria-label="Action filter" value={f.action} onChange={set("action")}><option value="">All actions</option>{actions.data?.map((a) => <option key={a}>{a}</option>)}</Select>
          <Input type="date" aria-label="From" value={f.from} onChange={set("from")} /><Input type="date" aria-label="To" value={f.to} onChange={set("to")} />
        </div>
        {list.isLoading ? <TableSkeleton /> : list.isError ? <ErrorState message={(list.error as Error).message} onRetry={() => list.refetch()} />
          : list.data!.content.length === 0 ? <EmptyState title="No audit entries" /> : (
          <Table><thead><tr><Th>When</Th><Th>Actor</Th><Th>Action</Th><Th>Entity</Th><Th>Details</Th><Th>Source</Th><Th>IP</Th></tr></thead>
            <tbody>{list.data!.content.map((a) => (
              <tr key={a.id}><Td className="whitespace-nowrap text-xs">{formatDateTime(a.occurred_at)}</Td>
                <Td><p className="font-semibold">{a.actor_email}</p><p className="text-xs text-navy-500">{a.actor_roles}</p></Td>
                <Td><Badge tone={a.action.includes("DELETE") || a.action.includes("CANCEL") ? "red" : a.action.includes("LOGIN") ? "neutral" : "blue"}>{a.action}</Badge></Td>
                <Td className="text-xs">{a.entity_type}<span className="block font-mono text-navy-500">{a.entity_id}</span></Td>
                <Td className="max-w-sm text-xs">{a.details ?? "—"}</Td><Td className="text-xs">{a.source}</Td><Td className="font-mono text-xs">{a.ip_address ?? "—"}</Td></tr>))}</tbody></Table>)}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
