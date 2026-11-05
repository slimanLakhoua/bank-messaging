import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { MessagePage, MessageSummary } from '../../data-access/message.models';
import { MessagesApi } from '../../data-access/messages-api';
import { MessageList } from './message-list';

const item: MessageSummary = {
  id: 7,
  sourceQueue: 'DEV.BANK.IN',
  jmsMessageId: 'ID:seven',
  correlationId: null,
  receivedAt: '2026-11-04T12:00:00Z',
  contentType: 'text/plain;charset=UTF-8',
  payloadSize: 25,
};

const pageOf = (content: readonly MessageSummary[], totalPages = 1): MessagePage => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages,
});

describe('MessageList', () => {
  const api = { list: vi.fn<MessagesApi['list']>() };
  let harness: RouterTestingHarness;
  let router: Router;

  beforeEach(async () => {
    api.list.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'messages', component: MessageList }]),
        { provide: MessagesApi, useValue: api },
      ],
    });
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  const root = () => harness.routeNativeElement as HTMLElement;
  const button = (label: string) =>
    Array.from(root().querySelectorAll('button')).find((b) => b.textContent?.includes(label));
  async function settle() {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  it('requests the page described by the URL and shows a loading state first', async () => {
    const pending = new Subject<MessagePage>();
    api.list.mockReturnValue(pending);

    await harness.navigateByUrl('/messages?page=2&size=50');

    expect(api.list).toHaveBeenCalledWith({ page: 2, size: 50 });
    expect(root().querySelector('[role="status"]')?.textContent).toContain('Chargement');
    expect(button('Actualiser')?.disabled).toBe(true);
  });

  it('renders one row per message with a formatted UTC date', async () => {
    api.list.mockReturnValue(of(pageOf([item])));

    await harness.navigateByUrl('/messages');

    expect(root().querySelectorAll('tbody tr')).toHaveLength(1);
    expect(root().textContent).toContain('ID:seven');
    expect(root().textContent).toContain('04/11/2026 12:00:00');
    expect(root().textContent).toContain('25 octets');
  });

  it('shows an empty state without a reset button on the first page', async () => {
    api.list.mockReturnValue(of(pageOf([], 0)));

    await harness.navigateByUrl('/messages');

    expect(root().textContent).toContain('Aucun message sur cette page');
    expect(button('Revenir au début')).toBeUndefined();
    expect(root().textContent).toContain('Page 1 sur 1');
  });

  it('offers to return to the first page when a later page is empty', async () => {
    api.list.mockReturnValue(of(pageOf([], 0)));
    await harness.navigateByUrl('/messages?page=3&size=20');

    button('Revenir au début')?.click();
    await settle();

    expect(router.url).toBe('/messages?page=0&size=20');
  });

  it('shows an alert on failure and retries on demand', async () => {
    api.list.mockReturnValueOnce(throwError(() => new Error('boom')));
    api.list.mockReturnValueOnce(of(pageOf([item])));
    await harness.navigateByUrl('/messages');

    expect(root().querySelector('[role="alert"]')?.textContent).toContain('Impossible de charger');
    expect(root().textContent).not.toContain('boom');

    button('Réessayer')?.click();
    await settle();

    expect(api.list).toHaveBeenCalledTimes(2);
    expect(root().textContent).toContain('ID:seven');
  });

  it('navigates between pages and keeps the page size', async () => {
    api.list.mockReturnValue(of(pageOf([item], 3)));
    await harness.navigateByUrl('/messages?page=1&size=50');

    button('Suivante')?.click();
    await settle();
    expect(router.url).toBe('/messages?page=2&size=50');

    button('Précédente')?.click();
    await settle();
    expect(router.url).toBe('/messages?page=1&size=50');
  });

  it('disables previous on the first page and next on the last page', async () => {
    api.list.mockReturnValue(of(pageOf([item], 2)));

    await harness.navigateByUrl('/messages?page=0');
    expect(button('Précédente')?.disabled).toBe(true);
    expect(button('Suivante')?.disabled).toBe(false);

    await harness.navigateByUrl('/messages?page=1');
    expect(button('Précédente')?.disabled).toBe(false);
    expect(button('Suivante')?.disabled).toBe(true);
  });

  it('resets to the first page when the page size changes', async () => {
    api.list.mockReturnValue(of(pageOf([item], 5)));
    await harness.navigateByUrl('/messages?page=3&size=20');

    const select = root().querySelector('#message-page-size') as HTMLSelectElement;
    select.value = '100';
    select.dispatchEvent(new Event('change'));
    await settle();

    expect(router.url).toBe('/messages?page=0&size=100');
    expect(api.list).toHaveBeenLastCalledWith({ page: 0, size: 100 });
  });

  it('links each row to its detail page with the current pagination', async () => {
    api.list.mockReturnValue(of(pageOf([item])));

    await harness.navigateByUrl('/messages?page=2&size=50');

    expect(root().querySelector('tbody a')?.getAttribute('href')).toBe(
      '/messages/7?page=2&size=50',
    );
  });
});
