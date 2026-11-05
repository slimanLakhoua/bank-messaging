import { ParamMap } from '@angular/router';
import { MessagePagination, parseMessagePagination } from './message-pagination';

export interface MessageSearch extends MessagePagination {
  readonly messageId?: string;
  readonly sourceQueue?: string;
  readonly from?: string;
  readonly to?: string;
  readonly sort?: 'receivedAt,asc' | 'receivedAt,desc';
}

export function parseMessageSearch(params: Pick<ParamMap, 'get'>): MessageSearch {
  const filters: Record<string, string> = {};
  for (const key of ['messageId', 'sourceQueue', 'from', 'to']) {
    const value = params.get(key)?.trim();
    if (value) {
      const isInstant =
        (key === 'from' || key === 'to') &&
        /(?:Z|[+-]\d{2}:\d{2})$/i.test(value) &&
        Number.isFinite(Date.parse(value));
      filters[key] = isInstant ? new Date(value).toISOString() : value;
    }
  }
  const sort = params.get('sort');
  return {
    ...parseMessagePagination(params),
    ...filters,
    ...(sort === 'receivedAt,asc' || sort === 'receivedAt,desc' ? { sort } : {}),
  };
}
