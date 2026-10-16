import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { AnalyticsApiService, AnalyticsStatistic } from '../../../services/analytics-api.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-instrument-stats-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './instrument-stats-modal.html',
  styleUrl: './instrument-stats-modal.css'
})
export class InstrumentStatsModalComponent implements OnChanges, OnDestroy {
  @Input() symbol = '';
  @Input() visible = false;
  @Output() closed = new EventEmitter<void>();

  statistic?: AnalyticsStatistic;
  loading = false;
  error = '';
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['symbol'] || changes['visible']) && this.visible && this.symbol) {
      this.loadStatistics();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  close(): void {
    this.closed.emit();
  }

  formatPercent(value: number | undefined): string {
    return value === undefined ? '—' : `${value.toFixed(2)}%`;
  }

  formatRatio(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : value.toFixed(2);
  }

  private loadStatistics(): void {
    this.loading = true;
    this.error = '';
    this.statistic = undefined;
    this.analyticsApi.getInsights([this.symbol], '1y', 30).pipe(takeUntil(this.destroy$)).subscribe({
      next: (insights) => {
        this.statistic = insights.statistics[0];
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.error = 'Statistics are temporarily unavailable.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }
}
