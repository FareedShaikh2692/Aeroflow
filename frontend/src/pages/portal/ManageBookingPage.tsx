import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { XCircle } from "lucide-react";
import { api, errorMessage } from "@/lib/api";
import type { Booking } from "@/lib/types";
import { formatMoney } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/dialog";
import { BookingSummary } from "@/components/BookingCard";
import { PnrForm, type PnrValues } from "@/components/PnrForm";

export default function ManageBookingPage() {
  const [creds, setCreds] = useState<PnrValues | null>(null);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [confirm, setConfirm] = useState(false);
  const find = useMutation({
    mutationFn: (v: PnrValues) => api<Booking>("/api/bookings/retrieve", { method: "POST", body: v }),
    onSuccess: (b, v) => { setBooking(b); setCreds(v); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const cancel = useMutation({
    mutationFn: () => api<Booking>(`/api/bookings/guest/${creds!.pnr}/cancel`, { method: "POST", body: { lastName: creds!.lastName } }),
    onSuccess: (b) => { setBooking(b); setConfirm(false); toast.success(`Booking cancelled. Refund of ${formatMoney(b.totalAmount, b.currency)} issued.`); },
    onError: (e) => { setConfirm(false); toast.error(errorMessage(e)); },
  });
  const canCancel = booking?.status === "CONFIRMED" && !booking.passengers.some((p) => p.checkedIn) && new Date(booking.departureTime) > new Date();
  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <div><h1 className="text-3xl font-extrabold">Manage your booking</h1><p className="text-navy-500">View, check in or cancel a reservation.</p></div>
      <Card><CardHeader title="Find your booking" /><CardBody><PnrForm onSubmit={(v) => find.mutate(v)} loading={find.isPending} /></CardBody></Card>
      {booking && (
        <BookingSummary booking={booking} actions={booking.status === "CONFIRMED" && (<>
          <Button asChild><Link to="/check-in">Check in</Link></Button>
          {canCancel && <Button variant="outline" className="text-red-600" onClick={() => setConfirm(true)}><XCircle />Cancel booking</Button>}
        </>)} />
      )}
      <ConfirmDialog open={confirm} onOpenChange={setConfirm} destructive title="Cancel this booking?" confirmLabel="Cancel booking" loading={cancel.isPending}
        description={<>Booking <strong>{booking?.pnr}</strong> will be cancelled for all travellers and seats released. A full refund of {formatMoney(booking?.totalAmount, booking?.currency)} is issued to the original card. This cannot be undone.</>}
        onConfirm={() => cancel.mutate()} />
    </div>
  );
}
