import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end acceptance tests. They run against a running EventPass instance:
 *   - local dev:   backend `mvn spring-boot:test-run` + `npm run dev`  (default http://localhost:5173)
 *   - packaged:    E2E_BASE_URL=http://localhost:8080 npm run e2e
 *   - deployed:    E2E_BASE_URL=https://your-app.example.com npm run e2e
 * Staff credentials default to the seeded demo accounts.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
  ],
})
