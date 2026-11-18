import { useMemo, useRef, type KeyboardEvent } from "react";
import type { Cabin } from "@/lib/types";
import { cn } from "@/lib/utils";

export interface SeatMapProps {
  businessRows: number;
  businessLayout: string;
  economyRows: number;
  economyLayout: string;
  occupied: string[];
  /** seats chosen in this session (seat -> label, e.g. passenger initials) */
  selected: Record<string, string>;
  /** only seats of this cabin are selectable */
  cabin: Cabin;
  /** a seat the active passenger already holds (shown as selected, selectable) */
  onSelect: (seat: string) => void;
  readOnly?: boolean;
}

interface Row { number: number; cabin: Cabin; groups: string[] }

/** Interactive aircraft seat map: aisles from '-' in the layout, roving keyboard focus, ARIA grid semantics. */
export function SeatMap(p: SeatMapProps) {
  const occupied = useMemo(() => new Set(p.occupied), [p.occupied]);
  const ref = useRef<HTMLDivElement>(null);
  const rows: Row[] = useMemo(() => {
    const out: Row[] = [];
    for (let r = 1; r <= p.businessRows; r++) out.push({ number: r, cabin: "BUSINESS", groups: p.businessLayout.split("-") });
    for (let r = p.businessRows + 1; r <= p.businessRows + p.economyRows; r++)
      out.push({ number: r, cabin: "ECONOMY", groups: p.economyLayout.split("-") });
    return out;
  }, [p.businessRows, p.businessLayout, p.economyRows, p.economyLayout]);

  const onKey = (e: KeyboardEvent<HTMLButtonElement>) => {
    const keys: Record<string, [number, number]> = { ArrowRight: [0, 1], ArrowLeft: [0, -1], ArrowDown: [1, 0], ArrowUp: [-1, 0] };
    const d = keys[e.key];
    if (!d || !ref.current) return;
    e.preventDefault();
    const el = e.currentTarget;
    const r = Number(el.dataset.r) + d[0];
    const c = Number(el.dataset.c) + d[1];
    ref.current.querySelector<HTMLButtonElement>(`button[data-r="${r}"][data-c="${c}"]`)?.focus();
  };

  let lastCabin: Cabin | null = null;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-4 text-xs font-medium text-navy-600" aria-hidden>
        <Legend className="border-navy-300 bg-white" label="Available" />
        <Legend className="border-navy-900 bg-navy-900" label="Selected" />
        <Legend className="border-navy-200 bg-navy-200" label="Occupied" />
        <Legend className="border-gold-400 bg-amber-50" label="Business" />
      </div>
      <div ref={ref} role="grid" aria-label="Aircraft seat map" className="mx-auto w-fit max-w-full overflow-x-auto rounded-[2.5rem] border border-navy-100 bg-gradient-to-b from-white to-navy-50/40 px-4 pb-6 pt-10 sm:px-8">
        {rows.map((row, ri) => {
          const header = row.cabin !== lastCabin;
          lastCabin = row.cabin;
          let ci = -1;
          return (
            <div key={row.number}>
              {header && (
                <div className="mb-2 mt-3 flex items-center gap-3 text-[11px] font-bold uppercase tracking-widest text-navy-500 first:mt-0">
                  <span className="h-px flex-1 bg-navy-200" />{row.cabin === "BUSINESS" ? "Business class" : "Economy class"}<span className="h-px flex-1 bg-navy-200" />
                </div>
              )}
              <div role="row" className="flex items-center justify-center gap-1.5 py-[3px]">
                <span className="w-6 text-right text-[11px] font-semibold text-navy-400" aria-hidden>{row.number}</span>
                {row.groups.map((g, gi) => (
                  <div key={gi} className={cn("flex gap-1.5", gi > 0 && "ml-5")}>
                    {g.split("").map((letter) => {
                      ci++;
                      const seat = `${row.number}${letter}`;
                      const taken = occupied.has(seat) && !p.selected[seat];
                      const mine = p.selected[seat];
                      const wrongCabin = row.cabin !== p.cabin;
                      const disabled = p.readOnly || taken || wrongCabin;
                      return (
                        <button key={seat} type="button" role="gridcell" data-r={ri} data-c={ci}
                          tabIndex={ri === 0 && ci === 0 ? 0 : -1} onKeyDown={onKey}
                          aria-label={`Seat ${seat}, ${row.cabin.toLowerCase()}, ${taken ? "occupied" : mine ? "selected" : "available"}`}
                          aria-selected={!!mine} aria-disabled={disabled} data-testid={`seat-${seat}`}
                          onClick={() => !disabled && p.onSelect(seat)}
                          className={cn(
                            "flex items-center justify-center rounded-md border text-[10px] font-bold transition",
                            row.cabin === "BUSINESS" ? "h-10 w-10" : "h-8 w-8",
                            mine ? "border-navy-900 bg-navy-900 text-white shadow-md"
                              : taken ? "cursor-not-allowed border-navy-200 bg-navy-200 text-transparent"
                              : row.cabin === "BUSINESS" ? "border-gold-400 bg-amber-50 text-navy-700 hover:bg-amber-100"
                              : "border-navy-300 bg-white text-navy-500 hover:border-accent-500 hover:bg-accent-50",
                            wrongCabin && !taken && !mine && "opacity-35 cursor-not-allowed hover:bg-inherit",
                          )}>
                          {mine || letter}
                        </button>
                      );
                    })}
                  </div>
                ))}
                <span className="w-6 text-[11px] font-semibold text-navy-400" aria-hidden>{row.number}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Legend({ className, label }: { className: string; label: string }) {
  return <span className="flex items-center gap-1.5"><span className={cn("size-4 rounded border", className)} />{label}</span>;
}
