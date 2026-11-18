import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { LayoutDashboard, LogOut, User } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";
import { Logo } from "../domain";
import { Button } from "../ui/button";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "../ui/misc";

export function PortalLayout() {
  const { user, isStaff, logout } = useAuth();
  const navigate = useNavigate();
  const link = ({ isActive }: { isActive: boolean }) =>
    cn("rounded-lg px-3 py-2 text-sm font-semibold transition", isActive ? "text-white bg-white/10" : "text-navy-200 hover:text-white");
  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-30 bg-navy-950/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6">
          <Link to="/" aria-label="AeroFlow home"><Logo light /></Link>
          <nav aria-label="Primary" className="hidden items-center gap-1 md:flex">
            <NavLink to="/" end className={link}>Book</NavLink>
            <NavLink to="/manage" className={link}>Manage booking</NavLink>
            <NavLink to="/check-in" className={link}>Check-in</NavLink>
            {user && !isStaff && <NavLink to="/my-trips" className={link}>My trips</NavLink>}
          </nav>
          {user ? (
            <Menu>
              <MenuTrigger asChild>
                <Button variant="outline" size="sm" className="border-white/20 bg-white/5 text-white hover:bg-white/10"><User />{user.firstName}</Button>
              </MenuTrigger>
              <MenuContent>
                {isStaff && <MenuItem onSelect={() => navigate("/admin")}><LayoutDashboard />Operations console</MenuItem>}
                {!isStaff && <MenuItem onSelect={() => navigate("/my-trips")}><User />My trips & profile</MenuItem>}
                <MenuItem danger onSelect={async () => { await logout(); navigate("/"); }}><LogOut />Sign out</MenuItem>
              </MenuContent>
            </Menu>
          ) : (
            <div className="flex gap-2">
              <Button asChild variant="ghost" size="sm" className="text-white hover:bg-white/10"><Link to="/login">Sign in</Link></Button>
              <Button asChild variant="accent" size="sm" className="hidden sm:inline-flex"><Link to="/register">Join AeroFlow</Link></Button>
            </div>
          )}
        </div>
        <nav aria-label="Primary mobile" className="flex gap-1 overflow-x-auto border-t border-white/5 px-3 py-1.5 md:hidden">
          <NavLink to="/" end className={link}>Book</NavLink>
          <NavLink to="/manage" className={link}>Manage</NavLink>
          <NavLink to="/check-in" className={link}>Check-in</NavLink>
          {user && !isStaff && <NavLink to="/my-trips" className={link}>My trips</NavLink>}
        </nav>
      </header>
      <main className="flex-1"><Outlet /></main>
      <footer className="border-t border-navy-100 bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-6 text-xs text-navy-500 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span>© {new Date().getFullYear()} AeroFlow — fictional airline, portfolio demonstration. Payments, e-mail and SMS are simulated.</span>
          <span className="flex gap-4"><Link to="/login" className="hover:text-navy-900">Staff sign-in</Link><a href="#top" className="hover:text-navy-900">Back to top</a></span>
        </div>
      </footer>
    </div>
  );
}
