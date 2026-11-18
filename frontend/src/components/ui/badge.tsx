import type { HTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badge = cva("inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset", {
  variants: {
    tone: {
      neutral: "bg-navy-50 text-navy-700 ring-navy-200",
      blue: "bg-accent-50 text-accent-700 ring-accent-100",
      green: "bg-emerald-50 text-emerald-700 ring-emerald-200",
      amber: "bg-amber-50 text-amber-800 ring-amber-200",
      red: "bg-red-50 text-red-700 ring-red-200",
      violet: "bg-violet-50 text-violet-700 ring-violet-200",
      gold: "bg-amber-50 text-amber-900 ring-amber-300",
    },
  },
  defaultVariants: { tone: "neutral" },
});

export function Badge({ className, tone, dot, children, ...p }: HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badge> & { dot?: boolean }) {
  return (
    <span className={cn(badge({ tone }), className)} {...p}>
      {dot && <span className="size-1.5 rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  );
}
