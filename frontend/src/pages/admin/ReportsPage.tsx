import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download, Info } from "lucide-react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, errorMessage, qs } from "@/lib/api";
import { downloadBlob, formatMoney, todayIso } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input } from "@/components/ui/form";
import { EmptyState, ErrorState, Table, TableSkeleton, Tabs, Td, Th } from "@/components/ui/misc";
import { PageHeader } from "@/components/domain";

type Row = Record<string, string | number | boolean | null>;
const TYPES = [{ value: "revenue", label: "Revenue" }, { value: "bookings", label: "Bookings" }, { value: "flights", label: "Flights" }, { value: "passengers", label: "Passengers" }];

export default function ReportsPage() {
  const [type, setType] = useState("revenue");
  const [from, setFrom] = useState(todayIso(-29));
  const [to, setTo] = useState(todayIso(type === "flights" ? 7 : 0));
  const [exporting, setExporting] = useState(false);
  const q = useQuery({ queryKey: ["report", type, from, to], queryFn: () => api<{ rows: Row[]; count: number }>(`/api/reports/${type}${qs({ from, to })}`) });
  const rows = q.data?.rows ?? [];
  const cols = rows[0] ? Object.keys(rows[0]) : [];
  const exportCsv = async () => {
    setExporting(true);
    try {
      downloadBlob(await api<Blob>(`/api/reports/${type}${qs({ from, to, format: "csv" })}`, { raw: true }), `aeroflow-${type}-${from}_${to}.csv`);
      toast.success("CSV exported");
    } catch (e) { toast.error(errorMessage(e)); } finally { setExporting(false); }
  };
  const chart = type === "revenue" ? Object.values(rows.reduce<Record<string, { date: string; net: number }>>((acc, r) => {
    const d = String(r.date); acc[d] ??= { date: d, net: 0 }; acc[d].net += Number(r.net); return acc;
  }, {})).sort((a, b) => a.date.localeCompare(b.date)) : [];
  const fmt = (k: string, v: Row[string]) => v == null ? "—" : typeof v === "boolean" ? (v ? "Yes" : "No")
    : /amount|gross|net|refunds|spend/.test(k) ? formatMoney(Number(v)) : /_at$|_time$|booking$/.test(k) && typeof v === "string" ? v.replace("T", " ").slice(0, 16) : String(v);
  return (
    <>
      <PageHeader title="Reports" description="Operational and commercial reporting with date filters and CSV export." crumbs={[{ label: "Console", to: "/admin" }, { label: "Reports" }]}
        actions={<Button onClick={exportCsv} loading={exporting} disabled={!rows.length}><Download />Export CSV</Button>} />
      <Card className="mb-6"><CardBody className="flex flex-wrap items-end gap-4">
        <Tabs value={type} onValueChange={setType} tabs={TYPES} />
        <Field label="From" htmlFor="rf"><Input id="rf" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
        <Field label="To" htmlFor="rt"><Input id="rt" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
      </CardBody></Card>
      {(type === "revenue" || type === "bookings" || type === "passengers") && (
        <p className="mb-4 flex items-center gap-2 text-xs text-amber-800"><Info className="size-3.5" />Rows flagged <strong>synthetic</strong> are generated demo history, not real transactions.</p>
      )}
      {type === "revenue" && chart.length > 0 && (
        <Card className="mb-6"><CardHeader title="Net revenue per day" description="AED" /><CardBody className="h-64">
          <ResponsiveContainer width="100%" height="100%"><BarChart data={chart}><CartesianGrid stroke="#e3ebf5" vertical={false} />
            <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} tick={{ fontSize: 11 }} /><YAxis tickFormatter={(v: number) => `${Math.round(v / 1000)}k`} tick={{ fontSize: 11 }} width={44} />
            <Tooltip formatter={(v: number) => formatMoney(v)} /><Bar dataKey="net" fill="#2563eb" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer>
        </CardBody></Card>
      )}
      <Card>
        <CardHeader title={`${TYPES.find((t) => t.value === type)?.label} report`} description={q.data ? `${q.data.count} rows` : undefined} />
        {q.isLoading ? <TableSkeleton cols={6} /> : q.isError ? <ErrorState message={(q.error as Error).message} onRetry={() => q.refetch()} />
          : rows.length === 0 ? <EmptyState title="No data for this period" /> : (
          <div className="max-h-[560px] overflow-auto"><Table><thead className="sticky top-0"><tr>{cols.map((c) => <Th key={c}>{c.replace(/_/g, " ")}</Th>)}</tr></thead>
            <tbody>{rows.slice(0, 300).map((r, i) => <tr key={i}>{cols.map((c) => <Td key={c} className="whitespace-nowrap text-xs">{fmt(c, r[c] ?? null)}</Td>)}</tr>)}</tbody></Table>
            {rows.length > 300 && <p className="p-4 text-center text-xs text-navy-500">Showing first 300 of {rows.length} rows - export CSV for the full report.</p>}</div>)}
      </Card>
    </>
  );
}
