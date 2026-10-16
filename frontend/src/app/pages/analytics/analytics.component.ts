import { CommonModule } from '@angular/common';
import { AfterViewChecked, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Subject, takeUntil } from 'rxjs';
import { InstrumentResponse } from '../../api/trading.models';
import {
  AnalyticsApiService,
  AnalyticsInsights,
  AnalyticsStatistic,
  PlotlyFigure
} from '../../services/analytics-api.service';
import { TradingApiService } from '../../api/trading-api.service';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './analytics.html',
  styleUrl: './analytics.css'
})
export class AnalyticsComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('correlationChart') private correlationChart?: ElementRef<HTMLElement>;
  @ViewChild('volatilityChart') private volatilityChart?: ElementRef<HTMLElement>;
  @ViewChild('assetClassChart') private assetClassChart?: ElementRef<HTMLElement>;

  instruments: InstrumentResponse[] = [];
  selectedSymbols: string[] = [];
  selectedAssetClass = 'ALL';
  period = '1y';
  volatilityWindow = 30;
  insights?: AnalyticsInsights;
  statistics: AnalyticsStatistic[] = [];
  loadingInstruments = true;
  loadingAnalytics = false;
  error = '';
  private pendingFigures?: { correlation: PlotlyFigure; volatility: PlotlyFigure; assetClass: PlotlyFigure };
  private readonly destroy$ = new Subject<void>();
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly tradingApi = inject(TradingApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.tradingApi.getInstruments(true).pipe(takeUntil(this.destroy$)).subscribe({
      next: (instruments) => {
        this.instruments = instruments;
        this.selectedSymbols = instruments.slice(0, 3).map((instrument) => instrument.symbol);
        this.loadingInstruments = false;
        this.cdr.markForCheck();
        this.runAnalytics();
      },
      error: () => {
        this.error = 'Unable to load the instrument universe.';
        this.loadingInstruments = false;
        this.cdr.markForCheck();
      }
    });
  }

  ngAfterViewChecked(): void {
    if (!this.pendingFigures || !this.correlationChart || !this.volatilityChart || !this.assetClassChart) {
      return;
    }

    const figures = this.pendingFigures;
    this.pendingFigures = undefined;
    void this.renderFigure(this.correlationChart.nativeElement, figures.correlation);
    void this.renderFigure(this.volatilityChart.nativeElement, figures.volatility);
    void this.renderFigure(this.assetClassChart.nativeElement, figures.assetClass);
  }

  ngOnDestroy(): void {
    if (this.correlationChart) Plotly.purge(this.correlationChart.nativeElement);
    if (this.volatilityChart) Plotly.purge(this.volatilityChart.nativeElement);
    if (this.assetClassChart) Plotly.purge(this.assetClassChart.nativeElement);
    this.destroy$.next();
    this.destroy$.complete();
  }

  get assetClasses(): string[] {
    return [...new Set(this.instruments.map((instrument) => instrument.assetClass))].sort();
  }

  get visibleInstruments(): InstrumentResponse[] {
    if (this.selectedAssetClass === 'ALL') return this.instruments;
    return this.instruments.filter((instrument) => instrument.assetClass === this.selectedAssetClass);
  }

  setAssetClass(assetClass: string): void {
    this.selectedAssetClass = assetClass;
    const allowed = new Set(this.visibleInstruments.map((instrument) => instrument.symbol));
    this.selectedSymbols = this.selectedSymbols.filter((symbol) => allowed.has(symbol));
  }

  toggleSymbol(symbol: string): void {
    this.selectedSymbols = this.selectedSymbols.includes(symbol)
      ? this.selectedSymbols.filter((selected) => selected !== symbol)
      : [...this.selectedSymbols, symbol];
  }

  runAnalytics(): void {
    if (this.selectedSymbols.length === 0) {
      this.error = 'Select at least one instrument.';
      return;
    }

    this.loadingAnalytics = true;
    this.error = '';
    forkJoin({
      insights: this.analyticsApi.getInsights(this.selectedSymbols, this.period, this.volatilityWindow),
      correlation: this.analyticsApi.getCorrelation(this.selectedSymbols, this.period),
      volatility: this.analyticsApi.getVolatility(this.selectedSymbols, this.period, this.volatilityWindow),
      assetClass: this.analyticsApi.getAssetClassVolatility(this.selectedSymbols, this.period)
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ insights, correlation, volatility, assetClass }) => {
        this.insights = insights;
        this.statistics = insights.statistics;
        this.pendingFigures = { correlation, volatility, assetClass };
        this.loadingAnalytics = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.error = 'Analytics could not be generated for the selected instruments.';
        this.loadingAnalytics = false;
        this.cdr.markForCheck();
      }
    });
  }

  formatPercent(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : `${value.toFixed(2)}%`;
  }

  formatRatio(value: number | null): string {
    return value === null ? '—' : value.toFixed(2);
  }

  private renderFigure(element: HTMLElement, figure: PlotlyFigure): Promise<unknown> {
    return Plotly.newPlot(element, figure.data, {
      ...figure.layout,
      autosize: true,
      paper_bgcolor: 'transparent',
      plot_bgcolor: 'transparent',
      font: { color: '#dbe2ed' }
    }, { responsive: true, displaylogo: false });
  }
}
