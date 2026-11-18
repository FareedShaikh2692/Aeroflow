import { CheckinFlow } from "@/components/CheckinFlow";

export default function CheckInPage() {
  return (
    <div>
      <div className="hero-sky px-4 py-10 sm:px-6"><div className="mx-auto max-w-4xl"><h1 className="text-3xl font-extrabold text-white">Online check-in</h1><p className="mt-1 text-navy-200">Choose your seat and get your boarding pass in under a minute.</p></div></div>
      <div className="mx-auto -mt-6 max-w-4xl px-4 pb-14 sm:px-6"><CheckinFlow /></div>
    </div>
  );
}
