import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SeatMap } from "./SeatMap";

const layout = { businessRows: 1, businessLayout: "AC-DF", economyRows: 2, economyLayout: "ABC-DEF", occupied: ["2B"] };

describe("SeatMap", () => {
  it("renders every seat with cabin-aware labels", () => {
    render(<SeatMap {...layout} selected={{}} cabin="ECONOMY" onSelect={() => {}} />);
    expect(screen.getAllByRole("gridcell")).toHaveLength(4 + 12);
    expect(screen.getByRole("gridcell", { name: "Seat 2B, economy, occupied" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("gridcell", { name: "Seat 1A, business, available" })).toHaveAttribute("aria-disabled", "true"); // wrong cabin
  });

  it("selects available seats and ignores occupied / wrong-cabin seats", async () => {
    const onSelect = vi.fn();
    render(<SeatMap {...layout} selected={{}} cabin="ECONOMY" onSelect={onSelect} />);
    await userEvent.click(screen.getByTestId("seat-2A"));
    await userEvent.click(screen.getByTestId("seat-2B"));
    await userEvent.click(screen.getByTestId("seat-1A"));
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith("2A");
  });

  it("shows selected seats with the traveller label", () => {
    render(<SeatMap {...layout} selected={{ "3C": "SA" }} cabin="ECONOMY" onSelect={() => {}} />);
    const seat = screen.getByTestId("seat-3C");
    expect(seat).toHaveTextContent("SA");
    expect(seat).toHaveAttribute("aria-selected", "true");
  });

  it("supports arrow-key navigation between seats", async () => {
    render(<SeatMap {...layout} selected={{}} cabin="BUSINESS" onSelect={() => {}} />);
    const first = screen.getByTestId("seat-1A");
    first.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByTestId("seat-1C")).toHaveFocus();
    await userEvent.keyboard("{ArrowDown}");
    expect(screen.getByTestId("seat-2B")).toHaveFocus();
  });

  it("is fully disabled in read-only mode", async () => {
    const onSelect = vi.fn();
    render(<SeatMap {...layout} selected={{}} cabin="ECONOMY" onSelect={onSelect} readOnly />);
    await userEvent.click(screen.getByTestId("seat-3A"));
    expect(onSelect).not.toHaveBeenCalled();
  });
});
