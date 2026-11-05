import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { parseMessageSearch } from '../../message-search';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { combineLatest, map, startWith, Subject, switchMap } from 'rxjs';
import { MESSAGE_PAGE_SIZES, MAX_MESSAGE_PAGE, MessagePageSize } from '../../message-pagination';
import { MessagesApi } from '../../data-access/messages-api';
import { toRequestState } from '../../data-access/request-state';

@Component({
  selector: 'app-message-list',
  imports: [RouterLink, DatePipe, FormsModule],
  templateUrl: './message-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageList {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(MessagesApi);
  private readonly refresh = new Subject<void>();

  protected readonly searchError = signal<string | null>(null);
  protected readonly pageSizes = MESSAGE_PAGE_SIZES;
  protected readonly maxPage = MAX_MESSAGE_PAGE;
  protected readonly pagination = toSignal(this.route.queryParamMap.pipe(map(parseMessageSearch)), {
    initialValue: parseMessageSearch(this.route.snapshot.queryParamMap),
  });
  protected readonly state = toSignal(
    combineLatest([this.route.queryParamMap, this.refresh.pipe(startWith(undefined))]).pipe(
      switchMap(([params]) => toRequestState(this.api.list(parseMessageSearch(params)))),
    ),
    { initialValue: { status: 'loading' } as const },
  );

  protected applySearch(form: NgForm): void {
    const values = form.value as {
      messageId: string;
      sourceQueue: string;
      from: string;
      to: string;
      sort: 'receivedAt,asc' | 'receivedAt,desc';
    };
    const from = values.from ? new Date(values.from + 'Z') : null;
    const to = values.to ? new Date(values.to + 'Z') : null;
    if (
      form.invalid ||
      (from && !Number.isFinite(from.getTime())) ||
      (to && !Number.isFinite(to.getTime())) ||
      (from && to && from >= to)
    ) {
      this.searchError.set('Vérifiez les filtres : le début doit précéder la fin (UTC).');
      return;
    }
    this.searchError.set(null);
    void this.router.navigate(['/messages'], {
      queryParams: {
        page: 0,
        size: this.pagination().size,
        messageId: values.messageId?.trim() || null,
        sourceQueue: values.sourceQueue?.trim() || null,
        from: from?.toISOString(),
        to: to?.toISOString(),
        sort: values.sort,
      },
    });
  }

  protected clearSearch(form: NgForm): void {
    form.resetForm({ messageId: '', sourceQueue: '', from: '', to: '', sort: 'receivedAt,desc' });
    this.searchError.set(null);
    void this.router.navigate(['/messages'], {
      queryParams: { page: 0, size: this.pagination().size },
    });
  }

  protected reload(): void {
    this.refresh.next();
  }

  protected go(page: number): void {
    void this.router.navigate(['/messages'], {
      queryParams: { ...this.pagination(), page },
    });
  }

  protected resize(value: string): void {
    void this.router.navigate(['/messages'], {
      queryParams: { ...this.pagination(), page: 0, size: Number(value) as MessagePageSize },
    });
  }
}
