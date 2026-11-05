import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

test('IBM MQ → PostgreSQL → REST → Angular', async ({ page, request }) => {
  test.skip(process.env['RUN_LIVE'] !== '1', 'Requires the real backend and Compose services');
  const payload = `P4-browser-${Date.now()}-paiement-€`;
  execFileSync(
    'docker',
    [
      'compose',
      '--project-directory',
      resolve('..'),
      'exec',
      '-T',
      'mq',
      '/opt/mqm/samp/bin/amqsput',
      'DEV.BANK.IN',
      'QM1',
    ],
    { input: payload + '\n\n' },
  );
  let id: number | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get('/api/messages?size=20');
        if (!response.ok()) return false;
        for (const message of (await response.json()).content) {
          const detail = await request.get(`/api/messages/${message.id}`);
          if ((await detail.json()).payload === payload) {
            id = message.id;
            return true;
          }
        }
        return false;
      },
      { timeout: 20000 },
    )
    .toBe(true);
  await page.goto('/messages');
  await page.getByRole('link', { name: `Voir le message ${id}`, exact: true }).click();
  await expect(page.locator('pre')).toHaveText(payload);
  await expect(page.getByText('DEV.BANK.IN', { exact: true })).toBeVisible();
});
