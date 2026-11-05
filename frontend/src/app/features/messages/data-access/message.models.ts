export interface MessageSummary {
  readonly id: number;
  readonly sourceQueue: string;
  readonly jmsMessageId: string;
  readonly correlationId: string | null;
  readonly receivedAt: string;
  readonly contentType: string;
  readonly payloadSize: number;
}

export interface MessageDetail extends MessageSummary {
  readonly payload: string;
}

export interface MessagePage {
  readonly content: readonly MessageSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}
