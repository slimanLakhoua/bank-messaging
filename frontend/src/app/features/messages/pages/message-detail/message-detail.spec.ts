import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { MessageDetail } from '../../data-access/message.models';
import { MessagesApi } from '../../data-access/messages-api';
import { MessageDetailView } from './message-detail';

const detail: MessageDetail = {
  id: 7,
  sourceQueue: 'DEV.BANK.IN',
  jmsMessageId: 'ID:seven',
  correlationId: 'corr-1',
  receivedAt: '2026-11-04T12:00:00Z',
  contentType: 'text/plain;charset=UTF-8',
  payloadSize: 25,
  payload: 'hello',
};

describe('MessageDetailView', () => {
  const api = { detail: vi.fn<MessagesApi['detail']>() };
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    api.detail.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'messages/:id', component: MessageDetailView }]),
        { provide: MessagesApi, useValue: api },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  const root = () => harness.routeNativeElement as HTMLElement;
  const backHref = () => root().querySelector('a.back-link')?.getAttribute('href');

  it('loads the message identified by the route and shows a loading state first', async () => {
    api.detail.mockReturnValue(new Subject<MessageDetail>());

    await harness.navigateByUrl('/messages/7');

    expect(api.detail).toHaveBeenCalledWith('7');
    expect(root().querySelector('[role="status"]')?.textContent).toContain('Chargement');
  });

  it('renders metadata, formatted date and correlation id', async () => {
    api.detail.mockReturnValue(of(detail));

    await harness.navigateByUrl('/messages/7');

    const text = root().textContent;
    expect(text).toContain('ID:seven');
    expect(text).toContain('DEV.BANK.IN');
    expect(text).toContain('04/11/2026 12:00:00');
    expect(text).toContain('25 octets');
    expect(text).toContain('corr-1');
  });

  it('shows a placeholder when there is no correlation id', async () => {
    api.detail.mockReturnValue(of({ ...detail, correlationId: null }));

    await harness.navigateByUrl('/messages/7');

    expect(root().textContent).toContain('Non renseignée');
  });

  it('displays the payload as plain text', async () => {
    const payload = '<img src=x onerror="alert(1)">\nligne';
    api.detail.mockReturnValue(of({ ...detail, payload }));

    await harness.navigateByUrl('/messages/7');

    expect(root().querySelector('pre')?.textContent).toBe(payload);
    expect(root().querySelector('img')).toBeNull();
  });

  it('shows a not-found state without a retry button on 404', async () => {
    api.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    await harness.navigateByUrl('/messages/999');

    expect(root().querySelector('[role="alert"]')?.textContent).toContain('Message introuvable');
    expect(root().querySelector('button')).toBeNull();
  });

  it('shows a generic error and retries on demand', async () => {
    api.detail.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    api.detail.mockReturnValueOnce(of(detail));
    await harness.navigateByUrl('/messages/7');

    expect(root().querySelector('[role="alert"]')?.textContent).toContain(
      'Impossible de charger le message',
    );

    root().querySelector('button')?.click();
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(api.detail).toHaveBeenCalledTimes(2);
    expect(root().textContent).toContain('ID:seven');
  });

  it('keeps the list pagination in the back link and follows query changes', async () => {
    api.detail.mockReturnValue(of(detail));

    await harness.navigateByUrl('/messages/7?page=1&size=50');
    expect(backHref()).toBe('/messages?page=1&size=50');

    await harness.navigateByUrl('/messages/7?page=4&size=100');
    expect(backHref()).toBe('/messages?page=4&size=100');
  });

  it('falls back to default pagination for invalid query values', async () => {
    api.detail.mockReturnValue(of(detail));

    await harness.navigateByUrl('/messages/7?page=-1&size=13');

    expect(backHref()).toBe('/messages?page=0&size=20');
  });
});
