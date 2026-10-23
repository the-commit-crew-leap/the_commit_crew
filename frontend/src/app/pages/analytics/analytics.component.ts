import { CommonModule } from '@angular/common';
import { AfterViewChecked, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, forkJoin, map, of, Subject, takeUntil } from 'rxjs';
import { FundamentalsResponse, InstrumentResponse } from '../../api/trading.models';
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
  @ViewChild('volatilityChart') private volatilityChart?: ElementRef<HTMLElement>;
  @ViewChild('performanceChart') private performanceChart?: ElementRef<HTMLElement>;
  @ViewChild('drawdownChart') private drawdownChart?: ElementRef<HTMLElement>;
  @ViewChild('candlestickChart') private candlestickChart?: ElementRef<HTMLElement>;

  instruments: InstrumentResponse[] = [];
  selectedSymbols: string[] = [];
  selectedAssetClass = 'ALL';
  period = '1y';
  readonly periods = [
    { value: '1w', label: '1W' },
    { value: '1m', label: '1M' },
    { value: '6m', label: '6M' },
    { value: '1y', label: '1Y' },
    { value: '5y', label: '5Y' },
    { value: '10y', label: '10Y' }
  ];
  volatilityWindow = 30;
  insights?: AnalyticsInsights;
  statistics: AnalyticsStatistic[] = [];
  fundamentals: FundamentalsResponse[] = [];
  fundamentalsPrices: Record<string, number | null> = {};
  loadingFundamentals = false;
  fundamentalsError = '';
  loadingInstruments = true;
  loadingAnalytics = false;
  error = '';
  private pendingFigures?: { volatility: PlotlyFigure; performance: PlotlyFigure; drawdown: PlotlyFigure; candlestick?: PlotlyFigure | null };
  private readonly destroy$ = new Subject<void>();
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly tradingApi = inject(TradingApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.tradingApi.getInstruments(true).pipe(takeUntil(this.destroy$)).subscribe({
      next: (instruments) => {
        this.instruments = instruments;
        const requestedSymbol = this.route.snapshot.queryParamMap.get('symbol')?.trim().toUpperCase();
        const requestedInstrument = instruments.find((instrument) => instrument.symbol === requestedSymbol);
        this.selectedSymbols = requestedInstrument
          ? [requestedInstrument.symbol]
          : instruments.slice(0, 3).map((instrument) => instrument.symbol);
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
    if (!this.pendingFigures || !this.volatilityChart || !this.performanceChart || !this.drawdownChart) {
      return;
    }

    const figures = this.pendingFigures;
    this.pendingFigures = undefined;
    void this.renderFigure(this.volatilityChart.nativeElement, figures.volatility);
    void this.renderFigure(this.performanceChart.nativeElement, figures.performance);
    void this.renderFigure(this.drawdownChart.nativeElement, figures.drawdown);
    if (figures.candlestick && this.candlestickChart) {
      void this.renderFigure(this.candlestickChart.nativeElement, figures.candlestick);
    }
  }

  ngOnDestroy(): void {
    if (this.volatilityChart) Plotly.purge(this.volatilityChart.nativeElement);
    if (this.performanceChart) Plotly.purge(this.performanceChart.nativeElement);
    if (this.drawdownChart) Plotly.purge(this.drawdownChart.nativeElement);
    if (this.candlestickChart) Plotly.purge(this.candlestickChart.nativeElement);
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

  setPeriod(period: string): void {
    if (this.period === period) return;
    this.period = period;
  }

  runAnalytics(): void {
    if (this.selectedSymbols.length === 0) {
      this.error = 'Select at least one instrument.';
      return;
    }

    this.loadingAnalytics = true;
    this.loadingFundamentals = true;
    this.fundamentalsError = '';
    this.error = '';
    const volatilityWindow = this.getVolatilityWindow();
    forkJoin({
      insights: this.analyticsApi.getInsights(this.selectedSymbols, this.period, volatilityWindow),
      volatility: this.analyticsApi.getVolatility(this.selectedSymbols, this.period, volatilityWindow),
      drawdown: this.analyticsApi.getDrawdown(this.selectedSymbols, this.period),
      candlestick: this.selectedSymbols.length === 1
        ? this.analyticsApi.getCandlestick(this.selectedSymbols[0], this.period)
        : of(null),
      fundamentals: forkJoin(this.selectedSymbols.map((symbol) =>
        this.tradingApi.getInstrumentFundamentals(symbol).pipe(catchError(() => of(null)))
      )),
      prices: forkJoin(this.selectedSymbols.map((symbol) =>
        this.tradingApi.getInstrumentPrice(symbol).pipe(
          map((price) => price.price),
          catchError(() => of(null))
        )
      ))
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ insights, volatility, drawdown, candlestick, fundamentals, prices }) => {
        this.insights = insights;
        this.statistics = insights.statistics;
        this.pendingFigures = { volatility, performance: this.createPerformanceFigure(insights.statistics), drawdown, candlestick };
        this.fundamentals = fundamentals.filter(
          (fundamental): fundamental is FundamentalsResponse => fundamental !== null
        );
        this.fundamentalsError = this.fundamentals.length === 0
          ? 'SEC fundamentals are unavailable for the selected instruments.'
          : '';
        const currentPrices = prices as Array<number | null>;
        this.fundamentalsPrices = this.selectedSymbols.reduce<Record<string, number | null>>((result, symbol, index) => {
          result[symbol] = currentPrices[index] ?? null;
          return result;
        }, {});
        this.loadingAnalytics = false;
        this.loadingFundamentals = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.error = 'Analytics could not be generated for the selected instruments.';
        this.loadingAnalytics = false;
        this.loadingFundamentals = false;
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

  priceToEarnings(fundamental: FundamentalsResponse): number | null {
    const price = this.fundamentalsPrices[fundamental.symbol];
    const eps = fundamental.earningsPerShare;
    return price !== null && price !== undefined && eps !== null && eps > 0 ? price / eps : null;
  }

  returnOnEquity(fundamental: FundamentalsResponse): number | null {
    if (fundamental.netIncome === null || fundamental.totalAssets === null || fundamental.totalLiabilities === null) {
      return null;
    }
    const equity = fundamental.totalAssets - fundamental.totalLiabilities;
    return equity > 0 ? (fundamental.netIncome / equity) * 100 : null;
  }

  debtToEquity(fundamental: FundamentalsResponse): number | null {
    if (fundamental.totalLiabilities === null || fundamental.totalAssets === null) {
      return null;
    }
    const equity = fundamental.totalAssets - fundamental.totalLiabilities;
    return equity > 0 ? fundamental.totalLiabilities / equity : null;
  }

  formatFundamentalRatio(value: number | null): string {
    return value === null || !Number.isFinite(value) ? '—' : value.toFixed(2);
  }

  formatFundamentalPercent(value: number | null): string {
    return value === null || !Number.isFinite(value) ? '—' : `${value.toFixed(2)}%`;
  }

  private renderFigure(element: HTMLElement, figure: PlotlyFigure): Promise<unknown> {
    return Plotly.newPlot(element, figure.data, {
      ...figure.layout,
      autosize: true,
      width: element.clientWidth,
      height: Math.max(360, element.clientHeight),
      title: null,
      paper_bgcolor: 'transparent',
      plot_bgcolor: 'transparent',
      font: { color: '#b9c2d0', family: 'Inter, system-ui, sans-serif', size: 11 },
      margin: { l: 46, r: 18, t: 10, b: 42 },
      hovermode: 'x unified',
      hoverdistance: 20,
      spikedistance: -1,
      hoverlabel: { bgcolor: '#12161d', bordercolor: '#53658a', font: { color: '#f0f2f6', size: 11 } },
    }, {
      ...figure.config,
      responsive: true,
      displaylogo: false,
      displayModeBar: false,
      staticPlot: true,
      scrollZoom: false
    });
  }

  private createPerformanceFigure(statistics: AnalyticsStatistic[]): PlotlyFigure {
    const usableStatistics = statistics.filter((statistic) => (
      Number.isFinite(statistic['Annualized Return (%)'])
      && Number.isFinite(statistic['Annualized Volatility (%)'])
    ));

    return {
      data: [{
        type: 'scatter',
        mode: 'markers+text',
        x: usableStatistics.map((statistic) => statistic['Annualized Volatility (%)']),
        y: usableStatistics.map((statistic) => statistic['Annualized Return (%)']),
        text: usableStatistics.map((statistic) => statistic.Ticker),
        textposition: 'top center',
        textfont: { color: '#dbe2ed', size: 10 },
        customdata: usableStatistics.map((statistic) => [statistic.Ticker, statistic['Sharpe Ratio']]),
        hovertemplate: '<b>%{customdata[0]}</b><br>Risk: %{x:.2f}%<br>Return: %{y:.2f}%<br>Sharpe: %{customdata[1]:.2f}<extra></extra>',
        marker: {
          size: 18,
          color: usableStatistics.map((statistic) => statistic['Sharpe Ratio'] ?? 0),
          colorscale: [[0, '#ed7181'], [0.5, '#f2bf69'], [1, '#53d5ad']],
          showscale: true,
          colorbar: { title: 'Sharpe' },
          line: { color: '#0b0e12', width: 1 }
        }
      }],
      layout: {
        xaxis: { title: 'Annualized volatility (%)', zeroline: true, zerolinecolor: '#8993a3' },
        yaxis: { title: 'Annualized return (%)', zeroline: true, zerolinecolor: '#8993a3' },
        showlegend: false
      }
    };
  }

  private getVolatilityWindow(): number {
    switch (this.period) {
      case '1w': return Math.min(this.volatilityWindow, 2);
      case '1m': return Math.min(this.volatilityWindow, 5);
      case '6m': return Math.min(this.volatilityWindow, 10);
      default: return this.volatilityWindow;
    }
  }
}
