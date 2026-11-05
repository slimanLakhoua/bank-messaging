import { convertToParamMap } from '@angular/router';
import { parseMessagePagination } from './message-pagination';

describe('Message pagination', () => {
  it('defaults absent parameters to the first page with 20 messages', () => {
    expect(parseMessagePagination(convertToParamMap({}))).toEqual({ page: 0, size: 20 });
  });

  it.each([20, 50, 100])('accepts page size %i at the last supported page', (size) => {
    expect(
      parseMessagePagination(convertToParamMap({ page: '10000', size: String(size) })),
    ).toEqual({ page: 10000, size });
  });

  it.each(['-1', '1.5', '1e2', '', 'NaN', 'Infinity', '10001', '9007199254740993'])(
    'rejects invalid page %j while preserving a supported size',
    (page) => {
      expect(parseMessagePagination(convertToParamMap({ page, size: '50' }))).toEqual({
        page: 0,
        size: 50,
      });
    },
  );

  it.each(['2', '0', '-20', '20.0', '101', '', 'Infinity'])(
    'normalizes unsupported size %j without discarding the page',
    (size) => {
      expect(parseMessagePagination(convertToParamMap({ page: '3', size }))).toEqual({
        page: 3,
        size: 20,
      });
    },
  );
});
