import { HttpErrorResponse } from '@angular/common/http';
import { catchError, map, Observable, of, startWith, timeout } from 'rxjs';

export type RequestState<T> =
  | { readonly status: 'loading' }
  | { readonly status: 'ready'; readonly data: T }
  | { readonly status: 'error' }
  | { readonly status: 'missing' };

export function toRequestState<T>(request: Observable<T>): Observable<RequestState<T>> {
  return request.pipe(
    timeout({ first: 15_000 }),
    map((data): RequestState<T> => ({ status: 'ready', data })),
    catchError((error: unknown): Observable<RequestState<T>> =>
      of({
        status: error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'error',
      }),
    ),
    startWith({ status: 'loading' } as const),
  );
}
