import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessagesApi } from './messages-api';

describe('Messages API', () => {
  let api: MessagesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(MessagesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends pagination as GET query parameters and returns the response', () => {
    const response = { content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 };
    let result: unknown;
    api.list({ page: 2, size: 50 }).subscribe((value) => {
      result = value;
    });

    const request = http.expectOne('/api/messages?page=2&size=50');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(result).toEqual(response);
  });

  it('encodes a detail identifier so it cannot change the API path or query', () => {
    let status: number | undefined;
    api.detail('id/with?query#fragment').subscribe({
      error: (error) => {
        status = error.status;
      },
    });

    const request = http.expectOne('/api/messages/id%2Fwith%3Fquery%23fragment');
    expect(request.request.method).toBe('GET');
    request.flush({}, { status: 404, statusText: 'Not Found' });
    expect(status).toBe(404);
  });
});
