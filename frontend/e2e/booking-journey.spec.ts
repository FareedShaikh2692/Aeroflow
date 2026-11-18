import { expect, test, type Page } from "@playwright/test";

const PASSWORD = process.env.DEMO_PASSWORD ?? "Aeroflow@2026";
const tomorrow = () => new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);

async function signIn(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

test("passenger: login → search → book with seats → confirm → retrieve → check in → boarding pass", async ({ page }) => {
  await signIn(page, "passenger@aeroflow.dev");
  await expect(page).toHaveURL(/\/my-trips/);
  await expect(page.getByRole("heading", { name: /Welcome, Sarah/ })).toBeVisible();

  // search
  await page.getByRole("link", { name: "Book", exact: true }).first().click();
  const search = page.getByRole("form", { name: "Search flights" });
  await search.getByLabel("From").selectOption("DXB");
  await search.getByLabel("To").selectOption("SIN");
  await search.getByLabel("Departure").fill(tomorrow());
  await search.getByLabel("Travellers").selectOption("2");
  await search.getByRole("button", { name: "Search" }).click();
  const result = page.getByTestId("flight-result").first();
  await expect(result).toBeVisible();
  await result.getByRole("link", { name: "Select" }).click();

  // travellers (traveller 1 prefilled from the signed-in profile)
  await expect(page.getByLabel("First name").first()).toHaveValue("Sarah");
  await page.locator("#t1-first").fill("Adam");
  await page.locator("#t1-last").fill("Ahmed");
  await page.locator("#t0-pp").fill("P1234567");
  await page.getByRole("button", { name: "Continue to seats" }).click();

  // seats: pick the first two free economy seats
  const free = page.locator('[data-testid^="seat-"][aria-disabled="false"][aria-selected="false"]');
  await expect(free.first()).toBeVisible();
  const first = (await free.nth(0).getAttribute("data-testid"))!.replace("seat-", "");
  await free.nth(0).click();
  const second = (await free.nth(0).getAttribute("data-testid"))!.replace("seat-", "");
  await free.nth(0).click();
  await expect(page.getByText(`Traveller 1 · Economy · ${first}`)).toBeVisible();
  await expect(page.getByText(`Traveller 2 · Economy · ${second}`)).toBeVisible();
  await page.getByRole("button", { name: "Continue to payment" }).click();

  // payment: validation first, then the approved test card
  await page.getByRole("button", { name: /^Pay / }).click();
  await expect(page.getByText("Card number is invalid")).toBeVisible();
  await page.getByLabel("Name on card").fill("Sarah Ahmed");
  await page.getByLabel("Card number").fill("4242 4242 4242 4242");
  await page.getByLabel("Expiry").fill("12/30");
  await page.getByLabel("CVV").fill("123");
  await page.getByLabel(/accept the fare rules/).check();
  await page.getByRole("button", { name: /^Pay / }).click();

  // confirmation
  await expect(page.getByRole("heading", { name: "You're booked!" })).toBeVisible();
  const pnr = (await page.getByTestId("pnr").textContent())!.trim();
  expect(pnr).toMatch(/^[A-Z0-9]{6}$/);
  await expect(page.getByText(`Seat ${first}`)).toBeVisible();
  await expect(page.getByText(`Seat ${second}`)).toBeVisible();

  // retrieve via manage booking
  await page.goto("/manage");
  await page.getByLabel("Booking reference").fill(pnr);
  await page.getByLabel("Passenger last name").fill("Ahmed");
  await page.getByRole("button", { name: "Find booking" }).click();
  await expect(page.getByText(pnr).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Cancel booking" })).toBeVisible();

  // online check-in
  await page.goto("/check-in");
  await page.getByLabel("Booking reference").fill(pnr);
  await page.getByLabel("Passenger last name").fill("Ahmed");
  await page.getByRole("button", { name: "Start check-in" }).click();
  await expect(page.getByText(/SIN/).first()).toBeVisible();
  await page.getByLabel("I accept the dangerous goods declaration").check();
  await page.getByRole("button", { name: /Check in 2 travellers/ }).click();
  await expect(page.getByTestId("boarding-pass")).toHaveCount(2);

  const download = page.waitForEvent("download");
  await page.getByTestId("boarding-pass").first().getByRole("button", { name: "Download PDF" }).click();
  expect((await download).suggestedFilename()).toMatch(/^boarding-pass-AF\d+-[A-Z0-9]{6}-\d{3}\.pdf$/);
});

test("staff RBAC: check-in agent cannot open user management; admin can", async ({ page }) => {
  await signIn(page, "checkin@aeroflow.dev");
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole("link", { name: "Users & roles" })).toHaveCount(0);
  await page.goto("/admin/users");
  await expect(page.getByText("You don't have access to this area")).toBeVisible();
});

test("admin: flights list filters and status change dialog", async ({ page }) => {
  await signIn(page, "admin@aeroflow.dev");
  await expect(page.getByRole("heading", { name: "Executive dashboard" })).toBeVisible();
  await page.getByRole("link", { name: "Flights" }).click();
  await page.getByLabel("Status filter").selectOption("SCHEDULED");
  await page.getByLabel("Search flights").fill("AF100");
  await expect(page.getByRole("link", { name: "AF100" }).first()).toBeVisible();
  await page.getByRole("button", { name: /Actions for AF100/ }).first().click();
  await page.getByRole("menuitem", { name: "Change status" }).click();
  await expect(page.getByRole("dialog")).toContainText("Update AF100 status");
  await page.getByRole("button", { name: "Cancel" }).click();
});
