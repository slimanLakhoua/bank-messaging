import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest, map, startWith, Subject, switchMap } from 'rxjs';
import { parseMessageSearch } from '../../message-search';
import { MessagesApi } from '../../data-access/messages-api';
import { toRequestState } from '../../data-access/request-state';

@Component({
  selector: 'app-message-detail',
  imports: [RouterLink, DatePipe],
  templateUrl: './message-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageDetailView {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(MessagesApi);
  private readonly refresh = new Subject<void>();

  protected readonly back = toSignal(this.route.queryParamMap.pipe(map(parseMessageSearch)), {
    initialValue: parseMessageSearch(this.route.snapshot.queryParamMap),
  });
  protected readonly state = toSignal(
    combineLatest([this.route.paramMap, this.refresh.pipe(startWith(undefined))]).pipe(
      switchMap(([params]) => toRequestState(this.api.detail(params.get('id') ?? ''))),
    ),
    { initialValue: { status: 'loading' } as const },
  );

  protected reload(): void {
    this.refresh.next();
  }
}
