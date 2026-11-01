import { existsSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { defineConfig, devices } from '@playwright/test'

// The settings ./stack.sh generated for the throwaway stack: its address and the demo accounts' password. They are
// read here and handed to the tests through the environment; nothing prints them.
const envFile = process.env.E2E_ENV_FILE ?? join(tmpdir(), 'stacc-e2e.env')
if (existsSync(envFile)) {
  for (const line of readFileSync(envFile, 'utf8').split('\n')) {
    const match = /^([A-Z_]+)=(.*)$/.exec(line)
    if (match && (match[1] === 'FRONTEND_PORT' || match[1] === 'STACC_TEST_PASSWORD')) {
      process.env[match[1]] ??= match[2]
    }
  }
}

const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${process.env.FRONTEND_PORT ?? '3100'}`

export default defineConfig({
  testDir: './tests',
  // The journeys share one seeded database and some change it, so they run one at a time, in file order.
  fullyParallel: false,
  workers: 1,
  // A flaky journey is a bug to fix, never something to retry until it passes.
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // On GitHub, failures also appear as annotations on the run's summary page.
  reporter: process.env.CI ? [['github'], ['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
  ],
})
