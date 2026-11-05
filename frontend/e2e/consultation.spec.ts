import { test, expect } from '@playwright/test';
const row = {
  id: 7,
  sourceQueue: 'DEV.BANK.IN',
  jmsMessageId: 'ID:demo-0007',
  correlationId: null,
  receivedAt: '2026-11-04T12:00:00Z',
  contentType: 'text/plain;charset=UTF-8',
  payloadSize: 35,
};
const pageOf = (page: number) => ({
  content: [{ ...row, id: page ? 8 : 7 }],
  page,
  size: 20,
  totalElements: 21,
  totalPages: 2,
});

test('pagination, detail, raw content and return to the same page', async ({ page }) => {
  await page.route('**/api/messages?*', (route) =>
    route.fulfill({
      json: pageOf(Number(new URL(route.request().url()).searchParams.get('page'))),
    }),
  );
  await page.route('**/api/messages/8', (route) =>
    route.fulfill({
      json: { ...row, id: 8, payload: '<script>window.hacked=true</script> €\nTest' },
    }),
  );
  await page.goto('/messages');
  await page.getByRole('button', { name: 'Suivante' }).click();
  await expect(page).toHaveURL(/page=1/);
  await page.getByRole('link', { name: 'Voir le message 8', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Détail du message' })).toBeVisible();
  await expect(page.locator('pre')).toHaveText('<script>window.hacked=true</script> €\nTest');
  expect(
    await page.evaluate(() => (window as Window & { hacked?: boolean }).hacked),
  ).toBeUndefined();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({ path: 'test-results/detail-mobile.png', fullPage: true });
  await page.getByRole('link', { name: 'Retour aux messages' }).click();
  await expect(page).toHaveURL(/messages\?page=1&size=20/);
  await expect(page.getByRole('button', { name: 'Suivante' })).toBeDisabled();
});

test('loading, error, retry and empty states', async ({ page }) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/messages?*', async (route) => {
    await gate;
    await route.fulfill({ status: 503, json: { detail: 'SQL secret' } });
  });
  await page.goto('/messages');
  await expect(page.getByRole('status')).toHaveText('Chargement des messages…');
  release();
  await expect(page.getByRole('alert')).toContainText('Impossible de charger');
  await expect(page.getByRole('alert')).not.toContainText('SQL secret');
  await page.unroute('**/api/messages?*');
  await page.route('**/api/messages?*', (route) =>
    route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }),
  );
  await page.getByRole('button', { name: 'Réessayer' }).click();
  await expect(page.getByRole('heading', { name: 'Aucun message sur cette page' })).toBeVisible();
});

test('detail not found and network retry', async ({ page }) => {
  await page.route('**/api/messages/9', (route) => route.fulfill({ status: 404, json: {} }));
  await page.goto('/messages/9');
  await expect(page.getByRole('heading', { name: 'Message introuvable' })).toBeVisible();
  await page.unroute('**/api/messages/9');
  await page.route('**/api/messages/9', (route) => route.abort());
  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Impossible de charger le message' }),
  ).toBeVisible();
  await page.unroute('**/api/messages/9');
  await page.route('**/api/messages/9', (route) =>
    route.fulfill({ json: { ...row, id: 9, payload: 'Reprise réussie' } }),
  );
  await page.getByRole('button', { name: 'Réessayer' }).click();
  await expect(page.locator('pre')).toHaveText('Reprise réussie');
});

for (const mode of ['light', 'dark', 'forced'] as const) {
  test(`keyboard and visual layout: ${mode}`, async ({ page }) => {
    await page.emulateMedia({
      colorScheme: mode === 'dark' ? 'dark' : 'light',
      forcedColors: mode === 'forced' ? 'active' : 'none',
    });
    await page.route('**/api/messages?*', (route) => route.fulfill({ json: pageOf(0) }));
    await page.goto('/messages');
    await expect(page.getByRole('link', { name: 'Voir le message 7', exact: true })).toBeVisible();
    const ratios = await page.evaluate(() => {
      function luminance(color: string) {
        const channels = color
          .match(/[\d.]+/g)!
          .slice(0, 3)
          .map(Number)
          .map((x) => x / 255)
          .map((x) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4));
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
      }
      function ratio(a: string, b: string) {
        const x = luminance(a),
          y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
      }
      const root = getComputedStyle(document.documentElement);
      const heading = getComputedStyle(document.querySelector('h1')!);
      const button = getComputedStyle(document.querySelector('button')!);
      const link = getComputedStyle(document.querySelector('td a')!);
      const input = getComputedStyle(document.querySelector('input')!);
      const table = getComputedStyle(document.querySelector('.table-scroll')!);
      return {
        heading: ratio(heading.color, root.backgroundColor),
        input: ratio(input.color, input.backgroundColor),
        inputBorder: ratio(input.borderColor, input.backgroundColor),
        button: ratio(button.color, button.backgroundColor),
        link: ratio(link.color, table.backgroundColor),
        border: ratio(button.borderColor, button.backgroundColor),
      };
    });
    expect(ratios.heading).toBeGreaterThanOrEqual(4.5);
    expect(ratios.input).toBeGreaterThanOrEqual(4.5);
    expect(ratios.inputBorder).toBeGreaterThanOrEqual(3);
    expect(ratios.button).toBeGreaterThanOrEqual(4.5);
    expect(ratios.link).toBeGreaterThanOrEqual(4.5);
    expect(ratios.border).toBeGreaterThanOrEqual(3);
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Aller au contenu' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('main')).toBeFocused();
    await page.screenshot({ path: `test-results/consultation-${mode}.png`, fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.screenshot({ path: `test-results/consultation-${mode}-mobile.png`, fullPage: true });
  });
}

test('changing page size returns to the first page', async ({ page }) => {
  await page.route('**/api/messages?*', (route) => {
    const url = new URL(route.request().url());
    return route.fulfill({
      json: {
        ...pageOf(Number(url.searchParams.get('page'))),
        size: Number(url.searchParams.get('size')),
      },
    });
  });
  await page.goto('/messages?page=1&size=20');
  await page.getByLabel('Messages par page').selectOption('50');
  await expect(page).toHaveURL(/page=0&size=50/);
  await expect(page.getByRole('button', { name: 'Précédente' })).toBeDisabled();
});

for (const path of ['/messages', '/messages/7']) {
  test(`a stalled request times out and can be retried: ${path}`, async ({ page }) => {
    const pattern = path === '/messages' ? '**/api/messages?*' : '**/api/messages/7';
    await page.route(pattern, () => {}); // Leave the connection pending rather than aborting it.
    await page.goto(path);
    await expect(page.getByRole('status')).toContainText('Chargement');
    await expect(page.getByRole('alert')).toContainText('Impossible de charger', {
      timeout: 20000,
    });
    await page.unroute(pattern);
    await page.route(pattern, (route) =>
      route.fulfill({
        json: path === '/messages' ? pageOf(0) : { ...row, payload: 'Reprise après délai' },
      }),
    );
    await page.getByRole('button', { name: 'Réessayer' }).click();
    if (path === '/messages')
      await expect(
        page.getByRole('link', { name: 'Voir le message 7', exact: true }),
      ).toBeVisible();
    else await expect(page.locator('pre')).toHaveText('Reprise après délai');
  });
}

test('an unsupported URL page size displays the normalized selection', async ({ page }) => {
  await page.route('**/api/messages?*', (route) => {
    expect(new URL(route.request().url()).searchParams.get('size')).toBe('20');
    return route.fulfill({ json: pageOf(0) });
  });
  await page.goto('/messages?size=2');
  await expect(page.getByLabel('Messages par page')).toHaveValue('20');
});

test('server filters and sort survive pagination and detail navigation', async ({ page }) => {
  await page.route('**/api/messages?*', (route) => route.fulfill({ json: pageOf(0) }));
  await page.route('**/api/messages/7', (route) =>
    route.fulfill({ json: { ...row, payload: 'filtered result' } }),
  );
  await page.goto('/messages?page=3');
  await page.getByLabel('Identifiant MQ exact').fill('ID:demo-0007');
  await page.getByLabel('File source exacte').fill('DEV.BANK.IN');
  await page.getByLabel('Depuis (UTC, inclus)').fill('2026-11-04T10:00');
  await page.getByLabel('Ordre de réception').selectOption('receivedAt,asc');
  const sent = page.waitForRequest(
    (request) => request.url().includes('/api/messages?') && request.url().includes('messageId='),
  );
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  const query = new URL((await sent).url()).searchParams;
  expect(query.get('page')).toBe('0');
  expect(query.get('messageId')).toBe('ID:demo-0007');
  expect(query.get('from')).toBe('2026-11-04T10:00:00.000Z');
  expect(query.get('sort')).toBe('receivedAt,asc');
  await page.getByRole('link', { name: 'Voir le message 7', exact: true }).click();
  await page.getByRole('link', { name: 'Retour aux messages' }).click();
  await expect(page.getByLabel('Identifiant MQ exact')).toHaveValue('ID:demo-0007');
  await page.getByRole('button', { name: 'Suivante', exact: true }).click();
  await expect(page).toHaveURL(/page=1/);
  expect(new URL(page.url()).searchParams.get('messageId')).toBe('ID:demo-0007');
  await page.getByRole('button', { name: 'Effacer les filtres' }).click();
  await expect(page.getByLabel('Identifiant MQ exact')).toHaveValue('');
  expect(new URL(page.url()).searchParams.has('messageId')).toBe(false);
});

test('invalid date range is explained before issuing a search', async ({ page }) => {
  let requests = 0;
  await page.route('**/api/messages?*', (route) => {
    requests++;
    return route.fulfill({ json: pageOf(0) });
  });
  await page.goto('/messages');
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await page.getByLabel('Depuis (UTC, inclus)').fill('2026-11-05T12:00');
  await page.getByLabel('Avant (UTC, exclu)').fill('2026-11-04T12:00');
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('le début doit précéder la fin');
  expect(requests).toBe(1);
});
