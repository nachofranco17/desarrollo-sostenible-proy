import { defineConfig } from '@playwright/test';

const port = Number(process.env.E2E_PORT ?? 5173);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('E2E_PORT inválido');
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './e2e',
  use: { baseURL, browserName: 'chromium', channel: 'chrome' },
  webServer: { command: `npm run dev -- --port ${port}`, url: baseURL, reuseExistingServer: !process.env.CI },
});
