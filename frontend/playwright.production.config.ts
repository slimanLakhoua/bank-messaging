import { defineConfig } from '@playwright/test';
import base from './playwright.config';

// Use the built application behind Nginx; never start the Angular development server.
export default defineConfig({
  ...base,
  webServer: undefined,
  use: { ...base.use, baseURL: process.env['APP_URL'] ?? 'http://127.0.0.1:8088' },
});
