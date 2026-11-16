// Playwright tests for Reagent's controlled input workaround
// (src/reagent/impl/input.cljs) using real, trusted browser input.
// Run with `npm run test:e2e`, which first builds the :e2e shadow-cljs build.
const { defineConfig, devices } = require('@playwright/test');

const port = 8091;

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
    reuseExistingServer: !process.env.CI,
  },
});
