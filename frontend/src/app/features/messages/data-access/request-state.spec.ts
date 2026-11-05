import { HttpErrorResponse } from '@angular/common/http';
import { TestScheduler } from 'rxjs/testing';
import { toRequestState } from './request-state';

describe('Request state', () => {
  let scheduler: TestScheduler;

  beforeEach(() => {
    scheduler = new TestScheduler((actual, expected) => expect(actual).toEqual(expected));
  });

  it('emits loading followed by the response', () => {
    scheduler.run(({ cold, expectObservable }) => {
      expectObservable(toRequestState(cold('--a|', { a: 'payload' }))).toBe('l-r|', {
        l: { status: 'loading' },
        r: { status: 'ready', data: 'payload' },
      });
    });
  });

  it.each([
    { error: new HttpErrorResponse({ status: 404 }), status: 'missing' },
    { error: new HttpErrorResponse({ status: 503, error: 'SQL secret' }), status: 'error' },
    { error: new Error('internal details'), status: 'error' },
  ])('maps $status without exposing internal error details', ({ error, status }) => {
    scheduler.run(({ cold, expectObservable }) => {
      expectObservable(toRequestState(cold('--#', {}, error))).toBe('l-(e|)', {
        l: { status: 'loading' },
        e: { status },
      });
    });
  });

  it('cancels a stalled request after 15 seconds and emits an error', () => {
    scheduler.run(({ cold, expectObservable, expectSubscriptions }) => {
      const request = cold('--------------------');
      expectObservable(toRequestState(request)).toBe('l 14999ms (e|)', {
        l: { status: 'loading' },
        e: { status: 'error' },
      });
      expectSubscriptions(request.subscriptions).toBe('^ 14999ms !');
    });
  });
});
