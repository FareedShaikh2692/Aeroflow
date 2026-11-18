import type { ReactNode } from "react";
import * as D from "@radix-ui/react-dialog";
import * as AD from "@radix-ui/react-alert-dialog";
import { X } from "lucide-react";
import { Button } from "./button";
import { cn } from "@/lib/utils";

const overlay = "fixed inset-0 z-50 bg-navy-950/50 backdrop-blur-[2px] data-[state=open]:animate-in";
const panel = "fixed left-1/2 top-1/2 z-50 w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white shadow-lift focus:outline-none max-h-[calc(100vh-2rem)] overflow-y-auto";

export function Dialog({ open, onOpenChange, title, description, children, footer, wide }: {
  open: boolean; onOpenChange: (o: boolean) => void; title: string; description?: string; children: ReactNode;
  footer?: ReactNode; wide?: boolean;
}) {
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className={overlay} />
        <D.Content className={cn(panel, wide ? "max-w-3xl" : "max-w-lg")}>
          <div className="flex items-start justify-between gap-4 border-b border-navy-100 px-6 py-4">
            <div>
              <D.Title className="font-display text-lg font-bold text-navy-900">{title}</D.Title>
              {description ? <D.Description className="mt-1 text-sm text-navy-500">{description}</D.Description>
                : <D.Description className="sr-only">{title}</D.Description>}
            </div>
            <D.Close asChild><Button variant="ghost" size="icon" aria-label="Close"><X /></Button></D.Close>
          </div>
          <div className="px-6 py-5">{children}</div>
          {footer && <div className="flex justify-end gap-2 border-t border-navy-100 bg-navy-50/60 px-6 py-3 rounded-b-2xl">{footer}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}

/** Accessible confirmation for destructive or irreversible actions. */
export function ConfirmDialog({ open, onOpenChange, title, description, confirmLabel = "Confirm", destructive, loading, onConfirm, children }: {
  open: boolean; onOpenChange: (o: boolean) => void; title: string; description: ReactNode; confirmLabel?: string;
  destructive?: boolean; loading?: boolean; onConfirm: () => void; children?: ReactNode;
}) {
  return (
    <AD.Root open={open} onOpenChange={onOpenChange}>
      <AD.Portal>
        <AD.Overlay className={overlay} />
        <AD.Content className={cn(panel, "max-w-md p-6")}>
          <AD.Title className="font-display text-lg font-bold text-navy-900">{title}</AD.Title>
          <AD.Description asChild><div className="mt-2 text-sm text-navy-600">{description}</div></AD.Description>
          {children && <div className="mt-4">{children}</div>}
          <div className="mt-6 flex justify-end gap-2">
            <AD.Cancel asChild><Button variant="outline" disabled={loading}>Cancel</Button></AD.Cancel>
            <Button variant={destructive ? "danger" : "primary"} loading={loading} onClick={onConfirm}>{confirmLabel}</Button>
          </div>
        </AD.Content>
      </AD.Portal>
    </AD.Root>
  );
}
