import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Mail, MessageSquare, Search } from "lucide-react";
import { api, qs } from "@/lib/api";
import type { NotificationItem, Page } from "@/lib/types";
import { formatDateTime } from "@/lib/utils";
import { Card } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorState, Pagination, TableSkeleton } from "@/components/ui/misc";
import { PageHeader } from "@/components/domain";

const TYPES = ["booking.confirmed", "booking.cancelled", "booking.flight-notice", "booking.checkin-reminder", "checkin.completed"];

export default function NotificationsPage() {
  const [f, setF] = useState({ q: "", channel: "", type: "" });
  const [page, setPage] = useState(0);
  const list = useQuery({ queryKey: ["notifications", f, page], queryFn: () => api<Page<NotificationItem>>(`/api/notifications${qs({ ...f, page, size: 20 })}`), placeholderData: keepPreviousData, refetchInterval: 15_000 });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setF({ ...f, [k]: e.target.value }); setPage(0); };
  return (
    <>
      <PageHeader title="Notifications" description="Event-driven e-mail and SMS, delivered through mock adapters (nothing is actually sent)." crumbs={[{ label: "Console", to: "/admin" }, { label: "Notifications" }]} />
      <Card>
        <div className="grid gap-3 border-b border-navy-100 p-4 sm:grid-cols-3">
          <div className="relative"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-navy-400" /><Input aria-label="Search notifications" placeholder="Recipient, PNR or subject" className="pl-9" value={f.q} onChange={set("q")} /></div>
          <Select aria-label="Channel" value={f.channel} onChange={set("channel")}><option value="">All channels</option><option>EMAIL</option><option>SMS</option></Select>
          <Select aria-label="Event type" value={f.type} onChange={set("type")}><option value="">All events</option>{TYPES.map((t) => <option key={t}>{t}</option>)}</Select>
        </div>
        {list.isLoading ? <TableSkeleton /> : list.isError ? <ErrorState message={(list.error as Error).message} onRetry={() => list.refetch()} />
          : list.data!.content.length === 0 ? <EmptyState title="No notifications yet" description="Bookings, flight changes and check-ins trigger messages automatically." /> : (
          <ul className="divide-y divide-navy-100">{list.data!.content.map((n) => (
            <li key={n.id} className="flex gap-4 px-5 py-4">
              <div className="rounded-lg bg-navy-50 p-2 text-navy-600">{n.channel === "SMS" ? <MessageSquare className="size-4" /> : <Mail className="size-4" />}</div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2"><p className="font-semibold">{n.subject ?? "SMS message"}</p><Badge tone="blue">{n.eventType}</Badge><Badge tone={n.status === "SENT" ? "green" : "red"}>{n.status}</Badge></div>
                <p className="text-xs text-navy-500">To {n.recipient} · {formatDateTime(n.createdAt)} {n.pnr && <>· PNR <strong>{n.pnr}</strong></>}</p>
                <p className="mt-2 whitespace-pre-line text-sm text-navy-700">{n.body}</p>
              </div>
            </li>))}</ul>)}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
