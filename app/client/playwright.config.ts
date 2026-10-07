import { defineConfig } from "@playwright/test";
import { APP_URL } from "./tests/health/env";

export default defineConfig({
  testDir: "./tests/health",
  globalSetup: "./tests/health/global-setup.ts",
  globalTeardown: "./tests/health/global-teardown.ts",
  workers: 1,
  fullyParallel: false,
  retries: process.env.CI === undefined ? 0 : 1,
  reporter:
    process.env.CI === undefined
      ? [["list"]]
      : [["list"], ["html", { open: "never" }]],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: APP_URL,

    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
    viewport: { width: 1680, height: 1200 },
  },
  projects: [
    {
      name: "chromium",
      use: {
        browserName: "chromium",

        launchOptions: {
          slowMo: Number(process.env.HEALTH_SLOWMO ?? 0),
        },
      },
    },
  ],
});
