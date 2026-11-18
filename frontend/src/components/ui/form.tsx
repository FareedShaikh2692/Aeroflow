import { forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import * as LabelPrimitive from "@radix-ui/react-label";
import { cn } from "@/lib/utils";

const field = "w-full rounded-lg border border-navy-200 bg-white px-3 text-sm text-navy-900 placeholder:text-navy-400 shadow-xs transition focus:border-accent-500 focus:outline-none focus:ring-3 focus:ring-accent-500/15 disabled:bg-navy-50 aria-[invalid=true]:border-red-400 aria-[invalid=true]:ring-red-500/10";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(({ className, ...p }, ref) => (
  <input ref={ref} className={cn(field, "h-10", className)} {...p} />
));
Input.displayName = "Input";

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(({ className, ...p }, ref) => (
  <textarea ref={ref} className={cn(field, "min-h-20 py-2", className)} {...p} />
));
Textarea.displayName = "Textarea";

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(({ className, children, ...p }, ref) => (
  <select ref={ref} className={cn(field, "h-10 pr-8 appearance-none bg-[url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2212%22 height=%2212%22 viewBox=%220 0 24 24%22 fill=%22none%22 stroke=%22%23466ba3%22 stroke-width=%222.5%22><path d=%22m6 9 6 6 6-6%22/></svg>')] bg-[right_0.75rem_center] bg-no-repeat", className)} {...p}>
    {children}
  </select>
));
Select.displayName = "Select";

export function Label({ className, ...p }: LabelPrimitive.LabelProps) {
  return <LabelPrimitive.Root className={cn("text-xs font-semibold uppercase tracking-wide text-navy-600", className)} {...p} />;
}

/** Label + control + error message with proper aria wiring. */
export function Field({ label, htmlFor, error, hint, children, className }: {
  label: string; htmlFor: string; error?: string; hint?: string; children: ReactNode; className?: string;
}) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error ? <p id={`${htmlFor}-error`} role="alert" className="text-xs font-medium text-red-600">{error}</p>
        : hint ? <p className="text-xs text-navy-500">{hint}</p> : null}
    </div>
  );
}
