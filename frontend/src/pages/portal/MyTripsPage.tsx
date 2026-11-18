import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Award, Bell, Plane } from "lucide-react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { Booking, NotificationItem, PassengerDetail } from "@/lib/types";
import { formatDateTime, formatMoney, formatNumber } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import { StatusBadge } from "@/components/domain";

export default function MyTripsPage() {
  const { user } = useAuth();
  const bookings = useQuery({ queryKey: ["my-bookings"], queryFn: () => api<Booking[]>("/api/bookings/my") });
  const profile = useQuery({ queryKey: ["my-profile"], queryFn: () => api<PassengerDetail>("/api/passengers/me"), retry: false });
  const notes = useQuery({ queryKey: ["my-notifications"], queryFn: () => api<NotificationItem[]>("/api/notifications/me") });
  const now = new Date();
  const upcoming = bookings.data?.filter((b) => b.status === "CONFIRMED" && new Date(b.departureTime) > now) ?? [];
  const past = bookings.data?.filter((b) => !upcoming.includes(b)) ?? [];

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div><h1 className="text-3xl font-extrabold">Welcome, {user?.firstName}</h1><p className="text-navy-500">Your trips, loyalty and messages.</p></div>
        <Button asChild variant="accent"><Link to="/"><Plane />Book a flight</Link></Button>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          <Card>
            <CardHeader title="Upcoming trips" />
            {bookings.isLoading ? <CardBody><Skeleton className="h-24" /></CardBody> : bookings.isError ? <ErrorState message={(bookings.error as Error).message} onRetry={() => bookings.refetch()} />
              : upcoming.length === 0 ? <EmptyState title="No upcoming trips" description="Your next adventure is a search away." /> : (
              <ul className="divide-y divide-navy-100">{upcoming.map((b) => <TripRow key={b.id} b={b} />)}</ul>)}
          </Card>
          <Card>
            <CardHeader title="Booking history" />
            {past.length === 0 ? <EmptyState title="No past bookings yet" /> : <ul className="divide-y divide-navy-100">{past.map((b) => <TripRow key={b.id} b={b} />)}</ul>}
          </Card>
        </div>
        <div className="space-y-6">
          <Card className="overflow-hidden">
            <div className="hero-sky p-5 text-white">
              <Award className="size-6 text-gold-400" />
              <p className="mt-3 text-xs uppercase tracking-widest text-navy-300">AeroFlow Skyward</p>
              <p className="font-display text-2xl font-extrabold">{profile.data?.profile.loyaltyTier ?? "BLUE"} tier</p>
              <p className="text-sm text-navy-200">{formatNumber(profile.data?.profile.loyaltyPoints ?? 0)} points · {profile.data?.totalTrips ?? 0} trips</p>
            </div>
          </Card>
          <Card>
            <CardHeader title={<span className="flex items-center gap-2"><Bell className="size-4 text-accent-600" />Messages</span>} description="Simulated e-mail & SMS" />
            {notes.data?.length ? (
              <ul className="max-h-[420px] divide-y divide-navy-100 overflow-y-auto">{notes.data.slice(0, 12).map((n) => (
                <li key={n.id} className="px-5 py-3 text-sm"><div className="flex items-center justify-between gap-2"><span className="font-semibold">{n.subject ?? "SMS"}</span><Badge tone={n.channel === "SMS" ? "violet" : "blue"}>{n.channel}</Badge></div>
                  <p className="mt-1 line-clamp-2 text-navy-500">{n.body}</p><p className="mt-1 text-xs text-navy-400">{formatDateTime(n.createdAt)}</p></li>))}</ul>
            ) : <EmptyState title="No messages yet" />}
          </Card>
        </div>
      </div>
    </div>
  );
}

function TripRow({ b }: { b: Booking }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
      <div>
        <p className="font-bold">{b.origin} → {b.destination} <span className="ml-1 text-sm font-medium text-navy-500">{b.flightNumber}</span></p>
        <p className="text-sm text-navy-500">{formatDateTime(b.departureTime)} · {b.passengers.length} traveller(s) · PNR <strong className="text-navy-800">{b.pnr}</strong></p>
      </div>
      <div className="flex items-center gap-3"><span className="text-sm font-semibold">{formatMoney(b.totalAmount, b.currency)}</span><StatusBadge status={b.status} /></div>
    </li>
  );
}
