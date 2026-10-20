import { CommonModule } from '@angular/common';
import { AfterViewChecked, ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IntradayPriceResponse, PriceResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { forkJoin, Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-instrument-stats-modal',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './instrument-stats-modal.html',
  styleUrl: './instrument-stats-modal.css'
})
export class InstrumentStatsModalComponent implements OnChanges, OnDestroy, AfterViewChecked {
  @ViewChild('volatilityChart') private volatilityChart?: ElementRef<HTMLElement>;
  @Input() symbol = '';
  @Input() visible = false;
  @Output() closed = new EventEmitter<void>();

  quote?: PriceResponse;
  intraday?: IntradayPriceResponse;
  loading = false;
  error = '';
  private pendingFigure?: { data: unknown[]; layout: Record<string, unknown> };
  private readonly tradingApi = inject(TradingApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['symbol'] || changes['visible']) && this.visible && this.symbol) {
      this.loadMarketData();
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
      title: null,
      margin: { l: 42, r: 12, t: 8, b: 38 },
      paper_bgcolor: 'transparent',
      plot_bgcolor: 'transparent',
      font: { color: '#b9c2d0', size: 10 },
      showlegend: false
    }, { responsive: true, displayModeBar: false, staticPlot: true, displaylogo: false });
  }

  close(): void {
    this.closed.emit();
  }

  formatPrice(value: number | null | undefined): string {
    return value === null || value === undefined
      ? '—'
      : value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  sessionHigh(): number | null {
    return this.intraday?.bars.length
      ? Math.max(...this.intraday.bars.map((bar) => bar.high))
      : null;
  }

  sessionLow(): number | null {
    return this.intraday?.bars.length
      ? Math.min(...this.intraday.bars.map((bar) => bar.low))
      : null;
  }

  private loadMarketData(): void {
    this.loading = true;
    this.error = '';
    this.quote = undefined;
    this.intraday = undefined;
    this.pendingFigure = undefined;
    forkJoin({
      quote: this.tradingApi.getInstrumentPrice(this.symbol),
      intraday: this.tradingApi.getInstrumentIntraday(this.symbol)
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ quote, intraday }) => {
        this.quote = quote;
        this.intraday = intraday;
        this.pendingFigure = this.createIntradayFigure(intraday);
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

  private createIntradayFigure(intraday: IntradayPriceResponse): { data: unknown[]; layout: Record<string, unknown> } {
    return {
      data: [{
        type: 'candlestick',
        x: intraday.bars.map((bar) => bar.timestamp),
        open: intraday.bars.map((bar) => bar.open),
        high: intraday.bars.map((bar) => bar.high),
        low: intraday.bars.map((bar) => bar.low),
        close: intraday.bars.map((bar) => bar.close),
        increasing: { line: { color: '#64d6a0' } },
        decreasing: { line: { color: '#ff929b' } }
      }],
      layout: { xaxis: { type: 'date', rangeslider: { visible: false } }, yaxis: { fixedrange: true } }
    };
  }
}
