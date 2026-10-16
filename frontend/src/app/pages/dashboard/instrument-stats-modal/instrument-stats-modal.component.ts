import { CommonModule } from '@angular/common';
import { AfterViewChecked, ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, inject } from '@angular/core';
import { AnalyticsApiService, AnalyticsStatistic, PlotlyFigure } from '../../../services/analytics-api.service';
import { forkJoin, Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-instrument-stats-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './instrument-stats-modal.html',
  styleUrl: './instrument-stats-modal.css'
})
export class InstrumentStatsModalComponent implements OnChanges, OnDestroy, AfterViewChecked {
  @ViewChild('volatilityChart') private volatilityChart?: ElementRef<HTMLElement>;
  @Input() symbol = '';
  @Input() visible = false;
  @Output() closed = new EventEmitter<void>();

  statistic?: AnalyticsStatistic;
  loading = false;
  error = '';
  selectedPeriod = '1y';
  readonly periods = [
    { value: '1y', label: '1Y' },
    { value: '5y', label: '5Y' },
    { value: '10y', label: '10Y' },
    { value: 'all', label: 'All' }
  ];
  private pendingFigure?: PlotlyFigure;
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['symbol'] || changes['visible']) && this.visible && this.symbol) {
      this.loadStatistics();
    }
  }

  ngOnDestroy(): void {
    if (this.volatilityChart) {
      Plotly.purge(this.volatilityChart.nativeElement);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngAfterViewChecked(): void {
    if (!this.pendingFigure || !this.volatilityChart) {
      return;
    }

    const figure = this.pendingFigure;
    this.pendingFigure = undefined;
    const chartElement = this.volatilityChart.nativeElement;
    void Plotly.newPlot(this.volatilityChart.nativeElement, figure.data, {
      ...figure.layout,
      autosize: true,
      width: chartElement.clientWidth,
      height: Math.max(320, chartElement.clientHeight),
      title: undefined,
      margin: { l: 42, r: 12, t: 8, b: 38 },
      paper_bgcolor: 'transparent',
      plot_bgcolor: 'transparent',
      font: { color: '#b9c2d0', size: 10 },
      showlegend: false
    }, { responsive: true, displaylogo: false });
  }

  close(): void {
    this.closed.emit();
  }

  changePeriod(period: string): void {
    if (this.selectedPeriod === period) {
      return;
    }

    this.selectedPeriod = period;
    this.loadStatistics();
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
    this.pendingFigure = undefined;
    forkJoin({
      insights: this.analyticsApi.getInsights([this.symbol], this.selectedPeriod, 30),
      volatility: this.analyticsApi.getVolatility([this.symbol], this.selectedPeriod, 30)
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ insights, volatility }) => {
        this.statistic = insights.statistics[0];
        this.pendingFigure = volatility;
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
