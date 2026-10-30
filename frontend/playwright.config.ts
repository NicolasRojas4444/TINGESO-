import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  timeout: 90000,
  expect: { timeout: 15000 },
  use: {
    baseURL: process.env.E2E_FRONTEND_URL,
    browserName: "chromium",
    channel: "chromium",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    // Only the isolated Docker test uses internal HTTP hostnames, not localhost.
    launchOptions: {
      args: process.env.E2E_SECURE_ORIGIN
        ? [
            `--unsafely-treat-insecure-origin-as-secure=${process.env.E2E_SECURE_ORIGIN}`,
          ]
        : [],
    },
  },
});
