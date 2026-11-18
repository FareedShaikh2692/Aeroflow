/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": path.resolve(__dirname, "src") } },
  server: {
    port: 5173,
    // In development the gateway is proxied so the app can use relative /api URLs.
    proxy: { "/api": { target: process.env.VITE_DEV_API_TARGET ?? "http://localhost:8080", changeOrigin: true } },
  },
  build: {
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ["react", "react-dom", "react-router-dom"],
          charts: ["recharts"],
          forms: ["react-hook-form", "zod", "@hookform/resolvers"],
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "lcov", "json-summary"],
      include: ["src/lib/**", "src/components/**", "src/pages/**"],
      exclude: ["src/**/*.test.*", "src/test/**"],
      // Quality gate on critical client logic (pages are covered by Playwright E2E).
      thresholds: {
        "src/lib/api.ts": { lines: 95, branches: 80 },
        "src/lib/schemas.ts": { lines: 95, branches: 90 },
        "src/lib/auth.tsx": { lines: 85, branches: 80 },
        "src/components/SeatMap.tsx": { lines: 95, branches: 90 },
      },
    },
  },
});
