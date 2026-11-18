import { defineConfig, devices } from "@playwright/test";

/**
 * E2E tests run against a live stack. Locally: backend via scripts/local-backend.sh (or docker compose)
 * and the Vite dev server (started automatically below). Set E2E_BASE_URL to test a deployed frontend.
 */
const external = !!process.env.E2E_BASE_URL;
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] }, testIgnore: /responsive/ },
    { name: "mobile", use: { ...devices["Pixel 7"] }, testMatch: /responsive/ },
  ],
  webServer: external ? undefined : { command: "pnpm dev", url: "http://localhost:5173", reuseExistingServer: true, timeout: 60_000 },
});
