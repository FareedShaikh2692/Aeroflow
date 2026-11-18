import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { BoardingPass, Page } from "@/lib/types";
import { formatDateTime } from "@/lib/utils";
import { Card, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { EmptyState, Table, TableSkeleton, Td, Th } from "@/components/ui/misc";
import { PageHeader } from "@/components/domain";
import { CheckinFlow } from "@/components/CheckinFlow";

export default function CheckinDeskPage() {
  const recent = useQuery({ queryKey: ["checkins-recent"], queryFn: () => api<Page<BoardingPass>>("/api/checkin?size=10"), refetchInterval: 20_000 });
  return (
    <>
      <PageHeader title="Check-in desk" description="Check passengers in, reassign seats and reprint boarding passes. Agents may override the online window."
        crumbs={[{ label: "Console", to: "/admin" }, { label: "Check-in desk" }]} />
      <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
        <CheckinFlow agent />
        <Card>
          <CardHeader title="Recent check-ins" />
          {recent.isLoading ? <TableSkeleton rows={4} cols={3} /> : !recent.data?.content.length ? <EmptyState title="No check-ins yet" /> : (
            <Table><thead><tr><Th>Passenger</Th><Th>Flight</Th><Th>Seat</Th></tr></thead>
              <tbody>{recent.data.content.map((c) => (
                <tr key={c.id}><Td><p className="font-semibold">{c.passengerName}</p><p className="text-xs text-navy-500">{formatDateTime(c.checkedInAt)}</p></Td>
                  <Td>{c.flightNumber}<Badge className="ml-1" tone={c.channel === "AGENT" ? "violet" : "blue"}>{c.channel}</Badge></Td><Td className="font-mono font-bold">{c.seatNumber}</Td></tr>))}</tbody></Table>)}
        </Card>
      </div>
    </>
  );
}
