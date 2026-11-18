import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Card({ className, ...p }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("rounded-xl border border-navy-100 bg-white shadow-card", className)} {...p} />;
}
export function CardHeader({ title, description, action, className }: {
  title: ReactNode; description?: ReactNode; action?: ReactNode; className?: string;
}) {
  return (
    <div className={cn("flex flex-wrap items-start justify-between gap-3 border-b border-navy-100 px-5 py-4", className)}>
      <div>
        <h3 className="text-base font-bold text-navy-900">{title}</h3>
        {description && <p className="mt-0.5 text-sm text-navy-500">{description}</p>}
      </div>
      {action}
    </div>
  );
}
export function CardBody({ className, ...p }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("p-5", className)} {...p} />;
}
