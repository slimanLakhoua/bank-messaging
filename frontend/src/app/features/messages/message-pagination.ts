import { ParamMap } from '@angular/router';

export const MESSAGE_PAGE_SIZES = [20, 50, 100] as const;
export type MessagePageSize = (typeof MESSAGE_PAGE_SIZES)[number];
export const DEFAULT_MESSAGE_PAGE_SIZE: MessagePageSize = 20;
export const MAX_MESSAGE_PAGE = 10_000;

export interface MessagePagination {
  readonly page: number;
  readonly size: MessagePageSize;
}

const isPageSize = (value: number): value is MessagePageSize =>
  (MESSAGE_PAGE_SIZES as readonly number[]).includes(value);

function parseUnsignedInt(raw: string | null): number | null {
  return raw !== null && /^\d+$/.test(raw) ? Number(raw) : null;
}

export function parseMessagePagination(params: Pick<ParamMap, 'get'>): MessagePagination {
  const page = parseUnsignedInt(params.get('page'));
  const size = parseUnsignedInt(params.get('size'));

  return {
    page: page !== null && page <= MAX_MESSAGE_PAGE ? page : 0,
    size: size !== null && isPageSize(size) ? size : DEFAULT_MESSAGE_PAGE_SIZE,
  };
}
