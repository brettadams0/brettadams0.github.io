import { defineConfig, devices } from '@playwright/test';

// BASE_URL=https://brettadams.ca runs the same suite against the live site.
const live = !!process.env.BASE_URL;
const baseURL = process.env.BASE_URL ?? 'http://127.0.0.1:4321';

export default defineConfig({
  testDir: './tests',
  timeout: 45_000,
  fullyParallel: true,
  retries: live ? 1 : 0,
  reporter: [['list']],
  use: { baseURL, trace: 'retain-on-failure' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
    { name: 'tablet-dark', use: { ...devices['Desktop Chrome'], viewport: { width: 768, height: 1024 }, colorScheme: 'dark' } },
  ],
  webServer: live
    ? undefined
    : { command: 'npm run preview -- --host 127.0.0.1', url: baseURL, reuseExistingServer: true, timeout: 60_000 },
});
