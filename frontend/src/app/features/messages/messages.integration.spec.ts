import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { MessagePage, MessageSummary } from './data-access/message.models';

const item: MessageSummary = {
  id: 7,
  sourceQueue: 'DEV.BANK.IN',
  jmsMessageId: 'ID:seven',
  correlationId: null,
  receivedAt: '2026-11-04T12:00:00Z',
  contentType: 'text/plain;charset=UTF-8',
  payloadSize: 25,
};

describe('Message consultation', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });
  const page = (content: readonly MessageSummary[], page = 0): MessagePage => ({
    content,
    page,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
  });
  const text = () => harness.routeNativeElement?.textContent ?? '';
  async function render() {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  it('sends URL filters to the server and keeps them in the detail return link', async () => {
    await harness.navigateByUrl(
      '/messages?page=2&size=20&messageId=ID%3Aseven&sourceQueue=DEV.BANK.IN&sort=receivedAt,asc',
    );
    const request = http.expectOne((req) => req.params.get('messageId') === 'ID:seven');
    expect(request.request.params.get('sourceQueue')).toBe('DEV.BANK.IN');
    expect(request.request.params.get('sort')).toBe('receivedAt,asc');
    request.flush(page([item]));
    await render();
    const link = harness.routeNativeElement!.querySelector('tbody a')!;
    expect(link.getAttribute('href')).toContain('messageId=ID:seven');
    await harness.navigateByUrl(
      '/messages/7?page=2&size=20&messageId=ID%3Aseven&sort=receivedAt,asc',
    );
    http.expectOne('/api/messages/7').flush({ ...item, payload: 'filtered' });
    await render();
    expect(harness.routeNativeElement!.querySelector('a')!.getAttribute('href')).toContain(
      'messageId=ID:seven',
    );
  });

  it('applies exact search and UTC date filters and resets pagination', async () => {
    await harness.navigateByUrl('/messages?page=3&size=20');
    http.expectOne('/api/messages?page=3&size=20').flush(page([]));
    await render();
    const root = harness.routeNativeElement!;
    const form = root.querySelector('form');
    expect(form).not.toBeNull();
    for (const [name, value] of Object.entries({
      messageId: 'ID:seven',
      sourceQueue: 'DEV.BANK.IN',
      from: '2026-11-04T12:00',
      to: '2026-11-05T12:00',
    })) {
      const input = root.querySelector(`input[name="${name}"]`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    form!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await render();
    const request = http.expectOne((req) => req.params.get('messageId') === 'ID:seven');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('from')).toBe('2026-11-04T12:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-11-05T12:00:00.000Z');
    request.flush(page([item]));
    await render();
    expect(text()).toContain('ID:seven');
  });

  it('keeps millisecond date precision when submitting an existing search', async () => {
    await harness.navigateByUrl('/messages?from=2026-11-04T12:00:00.123Z');
    http.expectOne((req) => req.params.get('from') === '2026-11-04T12:00:00.123Z').flush(page([]));
    await render();
    harness
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await render();
    const request = http.expectOne((req) => req.url === '/api/messages');
    expect(request.request.params.get('from')).toBe('2026-11-04T12:00:00.123Z');
    request.flush(page([]));
  });

  it('announces loading then an empty list', async () => {
    await harness.navigateByUrl('/messages');
    expect(text()).toContain('Chargement');
    http.expectOne('/api/messages?page=0&size=20').flush(page([]));
    await render();
    expect(text()).toContain('Aucun message');
  });
  it('renders metadata and a detail link that preserves pagination', async () => {
    await harness.navigateByUrl('/messages?page=2&size=20');
    http
      .expectOne('/api/messages?page=2&size=20')
      .flush({ ...page([item], 2), totalElements: 60, totalPages: 3 });
    await render();
    expect(text()).toContain('ID:seven');
    expect(
      harness.routeNativeElement?.querySelector('a[href*="/messages/7"]')?.getAttribute('href'),
    ).toContain('page=2');
  });
  it('shows a safe error and retries the request', async () => {
    await harness.navigateByUrl('/messages');
    http
      .expectOne('/api/messages?page=0&size=20')
      .flush({ detail: 'SQL secret' }, { status: 503, statusText: 'Unavailable' });
    await render();
    expect(text()).toContain('Impossible de charger');
    expect(text()).not.toContain('SQL secret');
    (harness.routeNativeElement?.querySelector('button') as HTMLButtonElement).click();
    http.expectOne('/api/messages?page=0&size=20').flush(page([item]));
    await render();
    expect(text()).toContain('ID:seven');
  });
  it('renders raw payload as text and supports a missing correlation', async () => {
    await harness.navigateByUrl('/messages/7?page=2&size=20');
    const payload = '<img src=x onerror="alert(1)"> €\nligne';
    http.expectOne('/api/messages/7').flush({ ...item, payload });
    await render();
    expect(harness.routeNativeElement?.querySelector('pre')?.textContent).toBe(payload);
    expect(harness.routeNativeElement?.querySelector('img')).toBeNull();
    expect(text()).toContain('Non renseignée');
    expect(harness.routeNativeElement?.querySelector('a')?.getAttribute('href')).toContain(
      'page=2',
    );
  });
  it('cancels the previous page request when navigation changes', async () => {
    await harness.navigateByUrl('/messages?page=0');
    const previous = http.expectOne('/api/messages?page=0&size=20');
    await harness.navigateByUrl('/messages?page=1');
    expect(previous.cancelled).toBe(true);
    http
      .expectOne('/api/messages?page=1&size=20')
      .flush({ ...page([item], 1), totalElements: 21, totalPages: 2 });
    await render();
    expect(text()).toContain('ID:seven');
  });
  it('updates the return link when query parameters change on the same detail page', async () => {
    await harness.navigateByUrl('/messages/7?page=1&size=20');
    http.expectOne('/api/messages/7').flush({ ...item, payload: 'message' });
    await render();
    await harness.navigateByUrl('/messages/7?page=2&size=50');
    await render();
    expect(harness.routeNativeElement?.querySelector('a')?.getAttribute('href')).toBe(
      '/messages?page=2&size=50',
    );
  });

  it('shows a dedicated not-found state', async () => {
    await harness.navigateByUrl('/messages/999');
    http.expectOne('/api/messages/999').flush({}, { status: 404, statusText: 'Not Found' });
    await render();
    expect(text()).toContain('Message introuvable');
  });
  it('normalizes invalid URL pagination without calling an invalid API', async () => {
    await harness.navigateByUrl('/messages?page=-3&size=101');
    http.expectOne('/api/messages?page=0&size=20').flush(page([]));
    await render();
    expect(TestBed.inject(Router).url).toContain('/messages');
  });
  for (const url of ['/messages', '/messages/7']) {
    it(`times out a stalled request and allows retry: ${url}`, async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
      await harness.navigateByUrl(url);
      const api = url === '/messages' ? '/api/messages?page=0&size=20' : '/api/messages/7';
      const pending = http.expectOne(api);
      await vi.advanceTimersByTimeAsync(15000);
      vi.useRealTimers();
      await render();
      expect(pending.cancelled).toBe(true);
      expect(text()).toContain('Impossible de charger');
      const retry = [...harness.routeNativeElement!.querySelectorAll('button')].find((button) =>
        button.textContent?.includes('Réessayer'),
      )!;
      retry.click();
      http
        .expectOne(api)
        .flush(url === '/messages' ? page([item]) : { ...item, payload: 'recovered' });
      await render();
      expect(text()).toContain(url === '/messages' ? 'ID:seven' : 'recovered');
    });
  }
  it('normalizes a valid API size unsupported by the UI selector', async () => {
    await harness.navigateByUrl('/messages?size=2');
    http.expectOne('/api/messages?page=0&size=20').flush(page([item]));
    await render();
    expect(
      harness.routeNativeElement?.querySelector<HTMLSelectElement>('#message-page-size')?.value,
    ).toBe('20');
  });
});
