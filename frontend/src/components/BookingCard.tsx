import type { ReactNode } from "react";
import type { Booking } from "@/lib/types";
import { formatDateTime, formatMoney } from "@/lib/utils";
import { Card } from "./ui/card";
import { Badge } from "./ui/badge";
import { StatusBadge } from "./domain";

export function BookingSummary({ booking, actions }: { booking: Booking; actions?: ReactNode }) {
  return (
    <Card className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-navy-100 bg-navy-900 px-5 py-4 text-white">
        <div>
          <p className="text-xs uppercase tracking-widest text-navy-300">Booking reference</p>
          <p className="font-display text-2xl font-extrabold tracking-[0.18em]">{booking.pnr}</p>
        </div>
        <StatusBadge status={booking.status} />
      </div>
      <div className="grid gap-4 p-5 sm:grid-cols-3">
        <div><p className="text-xs font-semibold uppercase text-navy-500">Flight</p><p className="font-bold">{booking.flightNumber} · {booking.origin} → {booking.destination}</p></div>
        <div><p className="text-xs font-semibold uppercase text-navy-500">Departure</p><p className="font-bold">{formatDateTime(booking.departureTime)}</p></div>
        <div><p className="text-xs font-semibold uppercase text-navy-500">Total</p><p className="font-bold">{formatMoney(booking.totalAmount, booking.currency)}</p></div>
      </div>
      <ul className="divide-y divide-navy-100 border-t border-navy-100">
        {booking.passengers.map((p) => (
          <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-sm">
            <span className="font-semibold">{p.firstName} {p.lastName}<span className="ml-2 font-normal text-navy-500">{p.cabin}</span></span>
            <span className="flex items-center gap-2">Seat <strong>{p.seatNumber}</strong>{p.checkedIn && <Badge tone="green">Checked in</Badge>}</span>
          </li>
        ))}
      </ul>
      {booking.status === "CANCELLED" && (
        <div className="border-t border-red-100 bg-red-50 px-5 py-3 text-sm text-red-700">Cancelled {formatDateTime(booking.cancelledAt)} — {booking.cancellationReason}</div>
      )}
      {actions && <div className="flex flex-wrap gap-2 border-t border-navy-100 px-5 py-4">{actions}</div>}
    </Card>
  );
}
