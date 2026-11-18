import { expect, test } from "@playwright/test";

test("mobile: portal navigation and search render without horizontal scroll", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /Fly further/ })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Primary mobile" })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});
