import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginForm, RegisterPage } from "./AuthPages";
import { jsonResponse, renderWithProviders } from "@/test/utils";

const session = (roles: string[]) => ({ accessToken: "a", refreshToken: "r", expiresIn: 900,
  user: { id: "1", email: "ops@aeroflow.dev", firstName: "Priya", lastName: "Nair", roles, enabled: true, createdAt: "" } });

describe("LoginForm", () => {
  it("shows validation errors without calling the API", async () => {
    const f = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 401)); // initial refresh attempt (no token -> not called)
    renderWithProviders(<LoginForm onSuccess={() => {}} />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Enter a valid e-mail address")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    expect(f).not.toHaveBeenCalled();
  });

  it("signs in and reports staff role", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(session(["OPERATIONS_MANAGER"])));
    const onSuccess = vi.fn();
    renderWithProviders(<LoginForm onSuccess={onSuccess} />);
    await userEvent.type(screen.getByLabelText("E-mail"), "ops@aeroflow.dev");
    await userEvent.type(screen.getByLabelText("Password"), "Aeroflow@2026");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(true));
    expect(localStorage.getItem("aeroflow.refreshToken")).toBe("r");
  });

  it("displays the server error message on bad credentials", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({ code: "INVALID_CREDENTIALS", message: "Invalid e-mail or password" }, 401));
    renderWithProviders(<LoginForm onSuccess={() => {}} />);
    await userEvent.type(screen.getByLabelText("E-mail"), "x@aeroflow.dev");
    await userEvent.type(screen.getByLabelText("Password"), "nope");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid e-mail or password");
  });

  it("demo account buttons fill the form", async () => {
    renderWithProviders(<LoginForm onSuccess={() => {}} />);
    await userEvent.click(screen.getByText("Demo accounts (password: Aeroflow@2026)"));
    await userEvent.click(screen.getByText("checkin@aeroflow.dev"));
    expect(screen.getByLabelText("E-mail")).toHaveValue("checkin@aeroflow.dev");
  });
});

describe("RegisterPage", () => {
  it("enforces the password policy and confirmation", async () => {
    renderWithProviders(<RegisterPage />);
    await userEvent.type(screen.getByLabelText("First name"), "Sarah");
    await userEvent.type(screen.getByLabelText("Last name"), "Ahmed");
    await userEvent.type(screen.getByLabelText("E-mail"), "sarah@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "weak");
    await userEvent.type(screen.getByLabelText("Confirm password"), "different");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("10+ characters with upper, lower, number and symbol")).toBeInTheDocument();
    expect(screen.getByText("Passwords do not match")).toBeInTheDocument();
  });
});
