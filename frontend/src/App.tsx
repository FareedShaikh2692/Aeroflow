import { lazy, Suspense, type ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { PERMISSIONS, useAuth } from "./lib/auth";
import type { Role } from "./lib/types";
import { PortalLayout } from "./components/layout/PortalLayout";
import { StaffLayout } from "./components/layout/StaffLayout";
import { LoginPage, RegisterPage } from "./pages/portal/AuthPages";
import { HomePage } from "./pages/portal/HomePage";

const SearchResultsPage = lazy(() => import("./pages/portal/SearchResultsPage"));
const BookPage = lazy(() => import("./pages/portal/BookPage"));
const ManageBookingPage = lazy(() => import("./pages/portal/ManageBookingPage"));
const CheckInPage = lazy(() => import("./pages/portal/CheckInPage"));
const MyTripsPage = lazy(() => import("./pages/portal/MyTripsPage"));
const DashboardPage = lazy(() => import("./pages/admin/DashboardPage"));
const OperationsPage = lazy(() => import("./pages/admin/OperationsPage"));
const FlightsPage = lazy(() => import("./pages/admin/FlightsPage"));
const FlightDetailPage = lazy(() => import("./pages/admin/FlightDetailPage"));
const PassengersPage = lazy(() => import("./pages/admin/PassengersPage"));
const PassengerDetailPage = lazy(() => import("./pages/admin/PassengerDetailPage"));
const BookingsPage = lazy(() => import("./pages/admin/BookingsPage"));
const CheckinDeskPage = lazy(() => import("./pages/admin/CheckinDeskPage"));
const ReportsPage = lazy(() => import("./pages/admin/ReportsPage"));
const AuditPage = lazy(() => import("./pages/admin/AuditPage"));
const NotificationsPage = lazy(() => import("./pages/admin/NotificationsPage"));
const UsersPage = lazy(() => import("./pages/admin/UsersPage"));
const SettingsPage = lazy(() => import("./pages/admin/SettingsPage"));

function Spinner() {
  return <div className="flex min-h-[50vh] items-center justify-center text-navy-400" role="status" aria-label="Loading"><Loader2 className="size-6 animate-spin" /></div>;
}

/** Route guard: requires a session and (optionally) one of the given roles. */
export function RequireRole({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, ready, hasRole } = useAuth();
  const location = useLocation();
  if (!ready) return <Spinner />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && !hasRole(...roles)) return <Forbidden />;
  return <>{children}</>;
}

function Forbidden() {
  return (
    <div className="mx-auto max-w-md py-20 text-center">
      <p className="font-display text-5xl font-extrabold text-navy-200">403</p>
      <h1 className="mt-2 text-xl font-bold">You don't have access to this area</h1>
      <p className="mt-2 text-sm text-navy-500">Ask an administrator to grant the required role.</p>
    </div>
  );
}

function NotFound() {
  return (
    <div className="mx-auto max-w-md py-24 text-center">
      <p className="font-display text-6xl font-extrabold text-navy-200">404</p>
      <h1 className="mt-2 text-xl font-bold">This page has flown away</h1>
      <a href="/" className="mt-4 inline-block text-sm font-semibold text-accent-600">Return home</a>
    </div>
  );
}

const guard = (roles: Role[], el: ReactNode) => <RequireRole roles={roles}>{el}</RequireRole>;

export default function App() {
  return (
    <Suspense fallback={<Spinner />}>
      <Routes>
        <Route element={<PortalLayout />}>
          <Route index element={<HomePage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="flights" element={<SearchResultsPage />} />
          <Route path="book/:flightId" element={<BookPage />} />
          <Route path="manage" element={<ManageBookingPage />} />
          <Route path="check-in" element={<CheckInPage />} />
          <Route path="my-trips" element={guard(["PASSENGER"], <MyTripsPage />)} />
        </Route>
        <Route path="admin" element={<RequireRole roles={PERMISSIONS.dashboard}><StaffLayout /></RequireRole>}>
          <Route index element={<DashboardPage />} />
          <Route path="operations" element={guard(PERMISSIONS.operations, <OperationsPage />)} />
          <Route path="flights" element={<FlightsPage />} />
          <Route path="flights/:id" element={<FlightDetailPage />} />
          <Route path="passengers" element={<PassengersPage />} />
          <Route path="passengers/:id" element={<PassengerDetailPage />} />
          <Route path="bookings" element={<BookingsPage />} />
          <Route path="check-in" element={guard(PERMISSIONS.checkin, <CheckinDeskPage />)} />
          <Route path="reports" element={guard(PERMISSIONS.reports, <ReportsPage />)} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="audit" element={guard(PERMISSIONS.audit, <AuditPage />)} />
          <Route path="users" element={guard(PERMISSIONS.users, <UsersPage />)} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}
