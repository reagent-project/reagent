// Playwright tests for Reagent's controlled input workaround
// (src/reagent/impl/input.cljs) using real, trusted browser input.
// Run with `npm run test:e2e`, which first builds the :e2e shadow-cljs build.
const { defineConfig, devices } = require('@playwright/test');

// Set E2E_PORT to run in several checkouts at the same time.
const port = Number(process.env.E2E_PORT || 8091);

module.exports = defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'node e2e/server.mjs',
    url: `http://127.0.0.1:${port}/`,
    env: { PORT: String(port) },
    // Never reuse: a server left running by another checkout would serve
    // that checkout's build.
    reuseExistingServer: false,
  },
});
