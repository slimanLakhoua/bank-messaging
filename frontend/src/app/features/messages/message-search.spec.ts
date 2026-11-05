import { convertToParamMap } from '@angular/router';
import { parseMessageSearch } from './message-search';

describe('Message search URL', () => {
  it('normalizes date offsets to UTC so form edits preserve the requested instant', () => {
    expect(
      parseMessageSearch(
        convertToParamMap({
          from: '2026-11-04T13:00:00+01:00',
          to: '2026-11-04T15:00:00+01:00',
          messageId: ' ID:seven ',
          sourceQueue: ' DEV.BANK.IN ',
          sort: 'receivedAt,asc',
        }),
      ),
    ).toEqual({
      page: 0,
      size: 20,
      from: '2026-11-04T12:00:00.000Z',
      to: '2026-11-04T14:00:00.000Z',
      messageId: 'ID:seven',
      sourceQueue: 'DEV.BANK.IN',
      sort: 'receivedAt,asc',
    });
  });
});
