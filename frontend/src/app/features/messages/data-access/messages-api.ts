import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MessageDetail, MessagePage } from './message.models';
import { MessageSearch } from '../message-search';

@Injectable({ providedIn: 'root' })
export class MessagesApi {
  private readonly http = inject(HttpClient);

  list(search: MessageSearch): Observable<MessagePage> {
    return this.http.get<MessagePage>('/api/messages', { params: { ...search } });
  }

  detail(id: string): Observable<MessageDetail> {
    return this.http.get<MessageDetail>(`/api/messages/${encodeURIComponent(id)}`);
  }
}
