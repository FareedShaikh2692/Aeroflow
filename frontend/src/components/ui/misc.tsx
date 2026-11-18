import type { HTMLAttributes, ReactNode } from "react";
import * as T from "@radix-ui/react-tabs";
import * as DM from "@radix-ui/react-dropdown-menu";
import * as CB from "@radix-ui/react-checkbox";
import { AlertTriangle, Check, ChevronLeft, ChevronRight, Inbox } from "lucide-react";
import { Button } from "./button";
import { cn } from "@/lib/utils";

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn("skeleton rounded-md", className)} />;
}

export function TableSkeleton({ rows = 6, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div className="space-y-3 p-5" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-4">{Array.from({ length: cols }).map((__, c) => <Skeleton key={c} className="h-5 flex-1" />)}</div>
      ))}
    </div>
  );
}

export function EmptyState({ title, description, action, icon }: { title: string; description?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
      <div className="mb-4 rounded-2xl bg-navy-50 p-4 text-navy-400">{icon ?? <Inbox className="size-7" />}</div>
      <h3 className="text-base font-bold text-navy-900">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-navy-500">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 rounded-2xl bg-red-50 p-3 text-red-600"><AlertTriangle className="size-6" /></div>
      <h3 className="font-bold text-navy-900">We couldn't load this</h3>
      <p className="mt-1 max-w-md text-sm text-navy-500">{message}</p>
      {onRetry && <Button variant="outline" className="mt-4" onClick={onRetry}>Try again</Button>}
    </div>
  );
}

export function Table({ className, ...p }: HTMLAttributes<HTMLTableElement>) {
  return <div className="overflow-x-auto"><table className={cn("w-full text-left text-sm", className)} {...p} /></div>;
}
export function Th({ className, ...p }: HTMLAttributes<HTMLTableCellElement>) {
  return <th className={cn("whitespace-nowrap border-b border-navy-100 bg-navy-50/70 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-navy-500", className)} {...p} />;
}
export function Td({ className, ...p }: HTMLAttributes<HTMLTableCellElement> & { colSpan?: number }) {
  return <td className={cn("border-b border-navy-50 px-4 py-3 align-middle text-navy-800", className)} {...p} />;
}

export function Pagination({ page, totalPages, total, onPage }: { page: number; totalPages: number; total: number; onPage: (p: number) => void }) {
  if (total === 0) return null;
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-navy-100 px-5 py-3 text-sm text-navy-500">
      <span>{total.toLocaleString()} result{total === 1 ? "" : "s"} · page {page + 1} of {Math.max(1, totalPages)}</span>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)} aria-label="Previous page"><ChevronLeft />Prev</Button>
        <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page">Next<ChevronRight /></Button>
      </div>
    </div>
  );
}

export function Tabs({ value, onValueChange, tabs, children }: { value: string; onValueChange: (v: string) => void; tabs: { value: string; label: string }[]; children?: ReactNode }) {
  return (
    <T.Root value={value} onValueChange={onValueChange}>
      <T.List className="inline-flex flex-wrap gap-1 rounded-xl bg-navy-100/70 p-1" aria-label="Sections">
        {tabs.map((t) => (
          <T.Trigger key={t.value} value={t.value} className="rounded-lg px-3.5 py-1.5 text-sm font-semibold text-navy-600 transition data-[state=active]:bg-white data-[state=active]:text-navy-900 data-[state=active]:shadow-sm">
            {t.label}
          </T.Trigger>
        ))}
      </T.List>
      {children}
    </T.Root>
  );
}

export function Checkbox({ id, checked, onCheckedChange, label, disabled }: { id: string; checked: boolean; onCheckedChange: (c: boolean) => void; label: ReactNode; disabled?: boolean }) {
  return (
    <div className="flex items-start gap-2.5">
      <CB.Root id={id} checked={checked} disabled={disabled} onCheckedChange={(c) => onCheckedChange(c === true)}
        className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-md border border-navy-300 bg-white data-[state=checked]:border-navy-900 data-[state=checked]:bg-navy-900 disabled:opacity-50">
        <CB.Indicator><Check className="size-3.5 text-white" strokeWidth={3} /></CB.Indicator>
      </CB.Root>
      <label htmlFor={id} className="text-sm text-navy-700">{label}</label>
    </div>
  );
}

export const Menu = DM.Root;
export const MenuTrigger = DM.Trigger;
export function MenuContent({ children, align = "end" }: { children: ReactNode; align?: "start" | "end" }) {
  return (
    <DM.Portal>
      <DM.Content align={align} sideOffset={6} className="z-50 min-w-44 rounded-xl border border-navy-100 bg-white p-1 shadow-lift">{children}</DM.Content>
    </DM.Portal>
  );
}
export function MenuItem({ children, onSelect, danger, disabled }: { children: ReactNode; onSelect: () => void; danger?: boolean; disabled?: boolean }) {
  return (
    <DM.Item disabled={disabled} onSelect={onSelect} className={cn("flex cursor-pointer select-none items-center gap-2 rounded-lg px-2.5 py-2 text-sm outline-none data-[highlighted]:bg-navy-50 data-[disabled]:pointer-events-none data-[disabled]:opacity-40 [&_svg]:size-4", danger ? "text-red-600" : "text-navy-800")}>
      {children}
    </DM.Item>
  );
}
