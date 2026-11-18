import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { RequireRole } from "./App";
import { tokenStore } from "./lib/api";
import { jsonResponse, renderWithProviders } from "./test/utils";

const session = (roles: string[]) => ({ accessToken: "a", refreshToken: "r", expiresIn: 900,
  user: { id: "1", email: "x@aeroflow.dev", firstName: "X", lastName: "Y", roles, enabled: true, createdAt: "" } });

function setup(roles: string[] | null, allowed: string[]) {
  if (roles) {
    localStorage.setItem("aeroflow.refreshToken", "r");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(session(roles)));
  } else tokenStore.set(null);
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<p>login page</p>} />
      <Route path="/secret" element={<RequireRole roles={allowed as never}><p>secret area</p></RequireRole>} />
    </Routes>, "/secret");
}

describe("RequireRole", () => {
  it("redirects anonymous users to login", async () => {
    setup(null, ["ADMIN"]);
    expect(await screen.findByText("login page")).toBeInTheDocument();
  });
  it("renders 403 for users without the role", async () => {
    setup(["CHECKIN_AGENT"], ["ADMIN"]);
    expect(await screen.findByText("You don't have access to this area")).toBeInTheDocument();
  });
  it("renders the page for permitted roles (session restored from refresh token)", async () => {
    setup(["ADMIN"], ["ADMIN", "SUPER_ADMIN"]);
    expect(await screen.findByText("secret area")).toBeInTheDocument();
  });
});
