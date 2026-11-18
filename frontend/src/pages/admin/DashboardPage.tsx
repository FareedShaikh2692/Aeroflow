import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Activity, AlertTriangle, BadgeDollarSign, CalendarClock, Info, Plane, PlaneTakeoff, RefreshCw, Ticket, Users, XCircle } from "lucide-react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "@/lib/api";
import type { Dashboard } from "@/lib/types";
import { cn, formatDateTime, formatMoney, formatNumber } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/misc";
import { PageHeader, StatCard } from "@/components/domain";

const STATUS_COLORS: Record<string, string> = { SCHEDULED: "#3b82f6", DELAYED: "#f59e0b", BOARDING: "#8b5cf6", DEPARTED: "#10b981", ARRIVED: "#94a3b8", CANCELLED: "#ef4444" };

export default function DashboardPage() {
  const q = useQuery({ queryKey: ["dashboard"], queryFn: () => api<Dashboard>("/api/reports/dashboard"), refetchInterval: 30_000 });
  const k = q.data?.kpis;
  return (
    <>
      <PageHeader title="Executive dashboard" description="Network performance across flights, bookings and revenue."
        crumbs={[{ label: "Console", to: "/admin" }, { label: "Dashboard" }]}
        actions={<Button variant="outline" onClick={() => q.refetch()} loading={q.isFetching}><RefreshCw />Refresh</Button>} />
      {q.data?.syntheticDataIncluded && (
        <div role="note" className="mb-5 flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <Info className="mt-0.5 size-4 shrink-0" />Revenue and booking history include <strong className="mx-1">synthetic demo data</strong> (last 90 days) generated for demonstration. Live bookings made in the app are added on top.
        </div>
      )}
      {q.isError ? <Card><ErrorState message={(q.error as Error).message} onRetry={() => q.refetch()} /></Card> : (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {!k ? Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-28 rounded-xl" />) : (<>
              <StatCard label="Total flights" value={formatNumber(k.total_flights)} hint={`${formatNumber(k.flights_today)} departing today`} icon={<Plane />} />
              <StatCard label="Active now" value={formatNumber(k.active_flights)} hint="Boarding or airborne" icon={<PlaneTakeoff />} tone="green" />
              <StatCard label="Delayed" value={formatNumber(k.delayed_flights)} hint="Current operational delays" icon={<CalendarClock />} tone="amber" />
              <StatCard label="Cancelled" value={formatNumber(k.cancelled_flights)} hint="All scheduled periods" icon={<XCircle />} tone="red" />
              <StatCard label="Bookings" value={formatNumber(k.total_bookings)} hint={`${formatNumber(k.bookings_today)} today · ${formatNumber(k.cancelled_bookings)} cancelled`} icon={<Ticket />} tone="blue" />
              <StatCard label="Passengers booked" value={formatNumber(k.passengers_booked)} hint={`${formatNumber(k.unique_customers)} customers · ${formatNumber(k.registered_passengers)} members`} icon={<Users />} tone="blue" />
              <StatCard label="Revenue (30 days)" value={formatMoney(k.revenue_30d)} hint="Net of refunds · includes synthetic data" icon={<BadgeDollarSign />} />
              <StatCard label="Forward load factor" value={`${k.load_factor}%`} hint={`${formatNumber(k.checkins_today)} check-ins today`} icon={<Activity />} tone="green" />
            </>)}
          </div>
          <div className="grid gap-6 xl:grid-cols-3">
            <Card className="xl:col-span-2">
              <CardHeader title="Bookings & revenue" description="Last 30 days (AED, net of refunds)" />
              <CardBody className="h-72">
                {q.data ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={q.data.bookingTrend} margin={{ left: 0, right: 8 }}>
                      <defs><linearGradient id="rev" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#2563eb" stopOpacity={0.35} /><stop offset="100%" stopColor="#2563eb" stopOpacity={0} /></linearGradient></defs>
                      <CartesianGrid stroke="#e3ebf5" vertical={false} />
                      <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} tick={{ fontSize: 11, fill: "#6a8cbd" }} />
                      <YAxis yAxisId="r" tickFormatter={(v: number) => `${Math.round(v / 1000)}k`} tick={{ fontSize: 11, fill: "#6a8cbd" }} width={44} />
                      <YAxis yAxisId="b" orientation="right" tick={{ fontSize: 11, fill: "#6a8cbd" }} width={32} />
                      <Tooltip formatter={(v: number, n: string) => (n === "revenue" ? formatMoney(v) : v)} />
                      <Area yAxisId="r" type="monotone" dataKey="revenue" stroke="#2563eb" strokeWidth={2} fill="url(#rev)" />
                      <Area yAxisId="b" type="monotone" dataKey="bookings" stroke="#0b1f3a" strokeWidth={1.5} fill="transparent" />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : <Skeleton className="h-full" />}
              </CardBody>
            </Card>
            <Card>
              <CardHeader title="Flight status" description="Departures ±3 days" />
              <CardBody className="h-72">
                {q.data && q.data.flightStatus.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={q.data.flightStatus} dataKey="count" nameKey="status" innerRadius={55} outerRadius={90} paddingAngle={2}>
                        {q.data.flightStatus.map((s) => <Cell key={s.status} fill={STATUS_COLORS[s.status] ?? "#94a3b8"} />)}
                      </Pie>
                      <Tooltip />
                    </PieChart>
                  </ResponsiveContainer>
                ) : q.data ? <EmptyState title="No flights in window" /> : <Skeleton className="h-full" />}
                <div className="-mt-4 flex flex-wrap justify-center gap-3 text-xs">
                  {q.data?.flightStatus.map((s) => <span key={s.status} className="flex items-center gap-1"><span className="size-2 rounded-full" style={{ background: STATUS_COLORS[s.status] }} />{s.status.toLowerCase()} {s.count}</span>)}
                </div>
              </CardBody>
            </Card>
          </div>
          <div className="grid gap-6 xl:grid-cols-3">
            <Card>
              <CardHeader title="Operational alerts" action={<Button asChild variant="link" size="sm"><Link to="/admin/operations">Ops control</Link></Button>} />
              {q.data?.alerts.length ? (
                <ul className="divide-y divide-navy-100">{q.data.alerts.map((a, i) => (
                  <li key={i} className="flex gap-3 px-5 py-3">
                    <AlertTriangle className={cn("mt-0.5 size-4 shrink-0", a.severity === "critical" ? "text-red-600" : a.severity === "warning" ? "text-amber-500" : "text-accent-600")} />
                    <div><p className="text-sm font-semibold">{a.title}</p><p className="text-xs text-navy-500">{a.detail}</p></div>
                  </li>))}</ul>
              ) : <EmptyState title="All clear" description="No delays, cancellations or capacity alerts." />}
            </Card>
            <Card>
              <CardHeader title="Top routes" description="Net revenue, 30 days" />
              <CardBody className="h-72">
                {q.data && <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={q.data.topRoutes} layout="vertical" margin={{ left: 10 }}>
                    <XAxis type="number" hide />
                    <YAxis type="category" dataKey="route" width={70} tick={{ fontSize: 11, fill: "#26406c" }} />
                    <Tooltip formatter={(v: number) => formatMoney(v)} />
                    <Bar dataKey="revenue" fill="#0b1f3a" radius={[0, 6, 6, 0]} />
                  </BarChart>
                </ResponsiveContainer>}
              </CardBody>
            </Card>
            <Card>
              <CardHeader title="Recent activity" />
              {q.data?.recentActivity.length ? (
                <ul className="max-h-80 divide-y divide-navy-100 overflow-y-auto">{q.data.recentActivity.map((a, i) => (
                  <li key={i} className="px-5 py-3"><p className="text-sm text-navy-800">{a.message}</p><p className="text-xs text-navy-400">{formatDateTime(a.occurred_at)}</p></li>))}</ul>
              ) : <EmptyState title="No recent activity" />}
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
