import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { BarChart3, Bell, CalendarClock, ClipboardCheck, FileSearch, LayoutDashboard, LogOut, Menu as MenuIcon, Plane,
  Settings, ShieldCheck, Ticket, Users, UserSquare2, X, Globe } from "lucide-react";
import { PERMISSIONS, useAuth } from "@/lib/auth";
import { ROLE_LABELS, type Role } from "@/lib/types";
import { cn, initials } from "@/lib/utils";
import { Logo } from "../domain";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "../ui/misc";

const NAV: { to: string; label: string; icon: typeof Plane; roles: Role[]; section: string }[] = [
  { to: "/admin", label: "Executive dashboard", icon: LayoutDashboard, roles: PERMISSIONS.dashboard, section: "Overview" },
  { to: "/admin/operations", label: "Operations control", icon: CalendarClock, roles: PERMISSIONS.operations, section: "Overview" },
  { to: "/admin/flights", label: "Flights", icon: Plane, roles: PERMISSIONS.flights, section: "Manage" },
  { to: "/admin/bookings", label: "Bookings", icon: Ticket, roles: PERMISSIONS.bookings, section: "Manage" },
  { to: "/admin/passengers", label: "Passengers", icon: UserSquare2, roles: PERMISSIONS.passengers, section: "Manage" },
  { to: "/admin/check-in", label: "Check-in desk", icon: ClipboardCheck, roles: PERMISSIONS.checkin, section: "Manage" },
  { to: "/admin/reports", label: "Reports", icon: BarChart3, roles: PERMISSIONS.reports, section: "Insight" },
  { to: "/admin/notifications", label: "Notifications", icon: Bell, roles: PERMISSIONS.notifications, section: "Insight" },
  { to: "/admin/audit", label: "Audit log", icon: FileSearch, roles: PERMISSIONS.audit, section: "Insight" },
  { to: "/admin/users", label: "Users & roles", icon: Users, roles: PERMISSIONS.users, section: "Administration" },
  { to: "/admin/settings", label: "Settings", icon: Settings, roles: PERMISSIONS.dashboard, section: "Administration" },
];

export function StaffLayout() {
  const { user, hasRole, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const items = NAV.filter((n) => hasRole(...n.roles));
  const sections = [...new Set(items.map((i) => i.section))];
  const name = `${user?.firstName ?? ""} ${user?.lastName ?? ""}`.trim();

  const sidebar = (
    <nav aria-label="Main" className="flex h-full flex-col">
      <div className="flex h-16 items-center justify-between px-5"><Logo light /><button className="lg:hidden text-navy-300" onClick={() => setOpen(false)} aria-label="Close menu"><X className="size-5" /></button></div>
      <div className="mx-4 mb-4 rounded-lg bg-white/5 px-3 py-2 text-[11px] font-semibold uppercase tracking-widest text-navy-300">Operations console</div>
      <div className="flex-1 space-y-5 overflow-y-auto px-3 pb-6">
        {sections.map((s) => (
          <div key={s}>
            <p className="px-3 pb-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-navy-400">{s}</p>
            {items.filter((i) => i.section === s).map((i) => (
              <NavLink key={i.to} to={i.to} end={i.to === "/admin"} onClick={() => setOpen(false)}
                className={({ isActive }) => cn("flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition",
                  isActive ? "bg-white text-navy-900 shadow-sm" : "text-navy-200 hover:bg-white/8 hover:text-white")}>
                <i.icon className="size-4" aria-hidden />{i.label}
              </NavLink>
            ))}
          </div>
        ))}
      </div>
      <NavLink to="/" className="mx-3 mb-4 flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-navy-300 hover:bg-white/8 hover:text-white"><Globe className="size-4" />Passenger portal</NavLink>
    </nav>
  );

  return (
    <div className="min-h-screen lg:pl-64">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 bg-navy-900 lg:block">{sidebar}</aside>
      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-navy-950/60" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-72 bg-navy-900 shadow-lift">{sidebar}</aside>
        </div>
      )}
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-navy-100 bg-white/85 px-4 backdrop-blur sm:px-6">
        <button className="rounded-lg p-2 text-navy-700 hover:bg-navy-50 lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu"><MenuIcon className="size-5" /></button>
        <div className="hidden items-center gap-2 text-sm text-navy-500 sm:flex"><ShieldCheck className="size-4 text-emerald-600" />Secure staff session</div>
        <Menu>
          <MenuTrigger asChild>
            <button className="flex items-center gap-3 rounded-xl py-1 pl-1 pr-3 hover:bg-navy-50" aria-label="Account menu">
              <span className="flex size-9 items-center justify-center rounded-lg bg-navy-900 text-xs font-bold text-white">{initials(name || "A F")}</span>
              <span className="hidden text-left sm:block">
                <span className="block text-sm font-semibold text-navy-900">{name}</span>
                <span className="block text-xs text-navy-500">{user?.roles.map((r) => ROLE_LABELS[r]).join(", ")}</span>
              </span>
            </button>
          </MenuTrigger>
          <MenuContent>
            <MenuItem onSelect={() => navigate("/admin/settings")}><Settings />Settings</MenuItem>
            <MenuItem danger onSelect={async () => { await logout(); navigate("/login"); }}><LogOut />Sign out</MenuItem>
          </MenuContent>
        </Menu>
      </header>
      <main className="mx-auto max-w-[1400px] px-4 py-6 sm:px-6 lg:py-8"><Outlet /></main>
    </div>
  );
}
