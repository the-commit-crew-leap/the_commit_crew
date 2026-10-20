import { CommonModule } from '@angular/common';
import { AfterViewChecked, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, exhaustMap, forkJoin, interval, of, startWith, Subject, Subscription, switchMap, takeUntil } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { InstrumentResponse, IntradayPriceResponse, OrderResponse, PositionResponse } from '../../api/trading.models';
import { TradingApiService } from '../../api/trading-api.service';
import { AnalyticsApiService, AnalyticsStatistic, PlotlyFigure, PortfolioScenario } from '../../services/analytics-api.service';

@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.css'
})
export class PortfolioComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('allocationChart') private allocationChart?: ElementRef<HTMLElement>;
  @ViewChild('cumulativeChart') private cumulativeChart?: ElementRef<HTMLElement>;
  @ViewChild('volatilityChart') private volatilityChart?: ElementRef<HTMLElement>;
  @ViewChild('drawdownChart') private drawdownChart?: ElementRef<HTMLElement>;
  @ViewChild('liveAllocationChart') private liveAllocationChart?: ElementRef<HTMLElement>;
  @ViewChild('liveValueChart') private liveValueChart?: ElementRef<HTMLElement>;
  @ViewChild('livePnlChart') private livePnlChart?: ElementRef<HTMLElement>;
  @ViewChild('livePriceChangeChart') private livePriceChangeChart?: ElementRef<HTMLElement>;

  readonly periods = [
    { value: '1w', label: '1W' }, { value: '1m', label: '1M' }, { value: '6m', label: '6M' },
    { value: '1y', label: '1Y' }, { value: '5y', label: '5Y' }, { value: '10y', label: '10Y' }
  ];
  positions: PositionResponse[] = [];
  orders: OrderResponse[] = [];
  instruments: InstrumentResponse[] = [];
  scenarioSymbol = '';
  scenarioQuantity = 1;
  scenario?: PortfolioScenario;
  cashBalance = 0;
  scenarioLoading = false;
  selectedPeriod = '1y';
  volatilityWindow = 30;
  statistics: AnalyticsStatistic[] = [];
  loading = true;
  error = '';
  liveError = '';
  liveLoading = false;
  viewMode: 'historical' | 'live' = 'live';
  liveUpdatedAt: Date | null = null;
  private accountId = '';
  private liveRefreshSubscription?: Subscription;
  private liveHistory: Array<{ time: string; value: number; pnl: number }> = [];
  private liveIntraday: IntradayPriceResponse[] = [];
  private pendingLiveFigures?: PlotlyFigure[];
  private pendingFigures?: { allocation: PlotlyFigure; cumulative: PlotlyFigure; volatility: PlotlyFigure; drawdown: PlotlyFigure };
  private readonly authSession = inject(AuthSessionService);
  private readonly tradingApi = inject(TradingApiService);
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  ngOnInit(): void {
    const accountId = this.authSession.getAccountId();
    if (!accountId) {
      this.error = 'No account found in session.';
      this.loading = false;
      return;
    }
    this.accountId = accountId;
    forkJoin({
      positions: this.tradingApi.getPositions(accountId),
      orders: this.tradingApi.getAccountOrders(accountId),
      instruments: this.tradingApi.getInstruments(true),
      balance: this.tradingApi.getBalance(accountId)
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ positions, orders, instruments, balance }) => {
        this.positions = positions;
        this.orders = orders;
        this.instruments = instruments;
        this.cashBalance = Number(balance.cashBalance.amount) || 0;
        this.scenarioSymbol = instruments[0]?.symbol ?? this.positions[0]?.symbol ?? '';
        this.runPortfolioAnalytics();
        this.startLiveRefresh();
      },
      error: () => {
        this.error = 'Unable to load your holdings.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  ngAfterViewChecked(): void {
    if (this.pendingFigures && this.allChartsReady()) {
      const figures = this.pendingFigures;
      this.pendingFigures = undefined;
      this.renderFigure(this.allocationChart!.nativeElement, figures.allocation);
      this.renderFigure(this.cumulativeChart!.nativeElement, figures.cumulative);
      this.renderFigure(this.volatilityChart!.nativeElement, figures.volatility);
      this.renderFigure(this.drawdownChart!.nativeElement, figures.drawdown);
    }
    if (this.pendingLiveFigures && this.allLiveChartsReady()) {
      const figures = this.pendingLiveFigures;
      this.pendingLiveFigures = undefined;
      [this.liveAllocationChart, this.liveValueChart, this.livePnlChart, this.livePriceChangeChart]
        .forEach((chart, index) => this.renderFigure(chart!.nativeElement, figures[index]));
    }
  }

  ngOnDestroy(): void {
    [this.allocationChart, this.cumulativeChart, this.volatilityChart, this.drawdownChart]
      .forEach((chart) => { if (chart) Plotly.purge(chart.nativeElement); });
    this.destroy$.next();
    this.destroy$.complete();
    this.liveRefreshSubscription?.unsubscribe();
  }

  setViewMode(mode: 'historical' | 'live'): void {
    if (this.viewMode === mode) {
      return;
    }

    this.viewMode = mode;
    if (mode === 'live') {
      this.startLiveRefresh();
    } else {
      this.liveRefreshSubscription?.unsubscribe();
      this.liveRefreshSubscription = undefined;
      this.liveError = '';
    }
  }

  livePortfolioValue(): number {
    return this.getValuedPositions().reduce((total, position) => total + (position.marketValue ?? 0), 0);
  }

  liveTotalValue(): number {
    return this.livePortfolioValue() + this.cashBalance;
  }

  liveUnrealizedPnL(): number {
    return this.getValuedPositions().reduce((total, position) => total + (position.unrealizedPnL ?? 0), 0);
  }

  liveHasUnavailablePrices(): boolean {
    return this.positions.some((position) => position.currentPrice === null || !position.live);
  }

  setPeriod(period: string): void {
    if (period !== this.selectedPeriod) {
      this.selectedPeriod = period;
      this.runPortfolioAnalytics();
    }
  }

  runScenario(): void {
    const valuedPositions = this.getValuedPositions();
    const symbols = valuedPositions.map((position) => position.symbol);
    const values = valuedPositions.map((position) => position.marketValue!);
    const acquisitionDates = symbols.map((symbol) => this.firstFilledBuyDate(symbol));
    if (!symbols.length || !this.scenarioSymbol || this.scenarioQuantity <= 0 || !Number.isInteger(this.scenarioQuantity)) {
      this.error = 'Choose an instrument and enter a positive quantity.';
      return;
    }
    this.scenarioLoading = true;
    this.error = '';
    this.analyticsApi.getPortfolioScenario(
      symbols, values, this.scenarioSymbol, this.scenarioQuantity,
      this.selectedPeriod, this.cashBalance, this.getVolatilityWindow(), acquisitionDates
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: (scenario) => {
        this.scenario = scenario;
        this.scenarioLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.error = 'The portfolio scenario could not be calculated.';
        this.scenarioLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  formatWeight(value: number): string {
    return `${(value * 100).toFixed(1)}%`;
  }

  scenarioMetric(snapshot: 'current' | 'proposed', key: string): number | null | undefined {
    return this.scenario?.[snapshot].statistics[0]?.[key as keyof AnalyticsStatistic] as number | null | undefined;
  }

  runPortfolioAnalytics(): void {
    const valuedPositions = this.getValuedPositions();
    const symbols = valuedPositions.map((position) => position.symbol);
    const values = valuedPositions.map((position) => position.marketValue!);
    const total = values.reduce((sum, value) => sum + value, 0);
    if (!symbols.length || total <= 0) {
      this.loading = false;
      this.error = 'Portfolio analytics are unavailable until market values load.';
      this.cdr.markForCheck();
      return;
    }

    const weights = values.map((value) => value / total);
    const acquisitionDates = symbols.map((symbol) => this.firstFilledBuyDate(symbol));
    const allocationTotal = total + this.cashBalance;
    const allocationSymbols = [...symbols, 'CASH'];
    const allocationWeights = [...values, this.cashBalance].map((value) => value / allocationTotal);
    const window = this.getVolatilityWindow();
    this.loading = true;
    this.error = '';
    forkJoin({
      insights: this.analyticsApi.getPortfolioInsights(symbols, weights, this.selectedPeriod, window, acquisitionDates),
      allocation: this.analyticsApi.getPortfolioAllocation(allocationSymbols, allocationWeights),
      cumulative: this.analyticsApi.getPortfolioCumulative(symbols, weights, this.selectedPeriod, acquisitionDates),
      volatility: this.analyticsApi.getPortfolioVolatility(symbols, weights, this.selectedPeriod, window, acquisitionDates),
      drawdown: this.analyticsApi.getPortfolioDrawdown(symbols, weights, this.selectedPeriod, acquisitionDates)
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ insights, allocation, cumulative, volatility, drawdown }) => {
        this.statistics = insights.statistics;
        this.pendingFigures = { allocation, cumulative, volatility, drawdown };
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.error = 'Portfolio analytics could not be generated.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  formatPercent(value: number | undefined): string {
    return value === undefined ? '—' : `${value.toFixed(2)}%`;
  }

  formatRatio(value: number | null): string {
    return value === null ? '—' : value.toFixed(2);
  }

  private getValuedPositions(): PositionResponse[] {
    return this.positions.filter((position) => position.marketValue !== null && position.marketValue > 0);
  }

  private firstFilledBuyDate(symbol: string): string {
    const dates = this.orders
      .filter((order) => order.symbol === symbol && order.side === 'BUY' && order.status === 'FILLED')
      .map((order) => order.createdOn.slice(0, 10))
      .sort();
    return dates[0] ?? '1900-01-01';
  }

  private startLiveRefresh(): void {
    this.liveRefreshSubscription?.unsubscribe();
    this.liveRefreshSubscription = interval(this.livePollingInterval()).pipe(
      startWith(0),
      exhaustMap(() => {
        this.liveLoading = true;
        return this.tradingApi.getPositions(this.accountId).pipe(
          switchMap((positions) => forkJoin({
            positions: of(positions),
            balance: this.tradingApi.getBalance(this.accountId),
            intraday: forkJoin(
              positions.map((position) => this.tradingApi.getInstrumentIntraday(position.symbol).pipe(
                catchError(() => of(null))
              ))
            )
          }))
        ).pipe(
          catchError((error) => {
            console.error('Failed to refresh live portfolio snapshot', error);
            this.liveError = 'Live portfolio data is temporarily unavailable.';
            return of(null);
          })
        );
      }),
      takeUntil(this.destroy$)
    ).subscribe((snapshot) => {
      this.liveLoading = false;
      if (!snapshot) {
        this.cdr.markForCheck();
        return;
      }

      this.positions = snapshot.positions;
      this.cashBalance = Number(snapshot.balance.cashBalance.amount) || 0;
      this.liveIntraday = snapshot.intraday.filter(
        (intraday): intraday is IntradayPriceResponse => intraday !== null
      );
      this.liveError = '';
      this.liveUpdatedAt = new Date();
      this.recordLiveSnapshot();
      this.cdr.markForCheck();
    });
  }

  private livePollingInterval(): number {
    const easternParts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/New_York',
      weekday: 'short',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23'
    }).formatToParts(new Date());
    const weekday = easternParts.find((part) => part.type === 'weekday')?.value;
    const hour = Number(easternParts.find((part) => part.type === 'hour')?.value);
    const minute = Number(easternParts.find((part) => part.type === 'minute')?.value);
    const marketOpen = hour > 9 || (hour === 9 && minute >= 30);
    const marketClosed = hour >= 16;
    const weekdaySession = weekday !== 'Sat' && weekday !== 'Sun';
    return weekdaySession && marketOpen && !marketClosed ? 5000 : 60000;
  }

  private recordLiveSnapshot(): void {
    this.liveHistory = this.calculateIntradayPortfolioHistory();
    this.pendingLiveFigures = this.createLiveFigures();
  }

  private calculateIntradayPortfolioHistory(): Array<{ time: string; value: number; pnl: number }> {
    const positionsBySymbol = new Map(this.positions.map((position) => [position.symbol, position]));
    const timestamps = [...new Set(this.liveIntraday.flatMap((intraday) =>
      intraday.bars.map((bar) => bar.timestamp)
    ))].sort();
    const latestPrices = new Map<string, number>();

    return timestamps.map((timestamp) => {
      for (const intraday of this.liveIntraday) {
        const bar = intraday.bars.filter((candidate) => candidate.timestamp <= timestamp).at(-1);
        if (bar) {
          latestPrices.set(intraday.symbol, bar.close);
        }
      }

      let holdingsValue = 0;
      let unrealizedPnl = 0;
      for (const [symbol, price] of latestPrices) {
        const position = positionsBySymbol.get(symbol);
        if (!position) {
          continue;
        }
        holdingsValue += position.quantity * price;
        unrealizedPnl += position.quantity * (price - position.averageCost);
      }

      return {
        time: timestamp,
        value: holdingsValue + this.cashBalance,
        pnl: unrealizedPnl
      };
    });
  }

  private createLiveFigures(): PlotlyFigure[] {
    const valuedPositions = this.getValuedPositions();
    const symbols = valuedPositions.map((position) => position.symbol);
    const values = valuedPositions.map((position) => position.marketValue ?? 0);
    const pnl = valuedPositions.map((position) => position.unrealizedPnL ?? 0);
    const axis = {
      showgrid: false,
      zeroline: false,
      showline: false,
      ticks: '',
      fixedrange: true,
      tickfont: { color: '#8993a3', size: 10 },
      tickformat: ',.0f'
    };
    const timeline = this.liveHistory.map((point) => point.time);
    return [
      {
        data: [{
          type: 'pie',
          labels: [...symbols, 'CASH'],
          values: [...values, this.cashBalance],
          hole: 0.66,
          textinfo: 'label+percent',
          textposition: 'outside',
          textfont: { color: '#b9c2d0', size: 10 },
          marker: { line: { color: '#151920', width: 2 } },
          hovertemplate: '%{label}<br>$%{value:,.2f}<br>%{percent}<extra></extra>'
        }],
        layout: { showlegend: true, legend: { orientation: 'h', y: -0.08, font: { color: '#8993a3', size: 10 } } }
      },
      {
        data: [{
          type: 'scatter', mode: 'lines', x: timeline, y: this.liveHistory.map((point) => point.value),
          line: { color: '#a7b8fa', width: 2.5, shape: 'spline' },
          fill: 'tozeroy', fillcolor: 'rgba(167,184,250,0.10)',
          hovertemplate: '%{x|%H:%M}<br>$%{y:,.2f}<extra></extra>'
        }],
        layout: { xaxis: { showgrid: false, fixedrange: true }, yaxis: axis }
      },
      {
        data: [{
          type: 'scatter', mode: 'lines', x: timeline, y: this.liveHistory.map((point) => point.pnl),
          line: { color: '#64d6a0', width: 2.5, shape: 'spline' },
          fill: 'tozeroy', fillcolor: 'rgba(100,214,160,0.09)',
          hovertemplate: '%{x|%H:%M}<br>%{y:+,.2f} USD<extra></extra>'
        }],
        layout: { xaxis: { showgrid: false, fixedrange: true }, yaxis: { ...axis, zeroline: true, zerolinecolor: '#36404e' } }
      },
      {
        data: [{
          type: 'bar', x: symbols, y: pnl,
          marker: { color: pnl.map((value) => value >= 0 ? '#64d6a0' : '#ff929b'), line: { width: 0 } },
          hovertemplate: '%{x}<br>%{y:+,.2f} USD<extra></extra>'
        }],
        layout: { xaxis: { showgrid: false, fixedrange: true, tickfont: { color: '#b9c2d0', size: 10 } }, yaxis: { ...axis, zeroline: true, zerolinecolor: '#36404e' } }
      }
    ].map((figure, index) => ({
      ...figure,
      layout: {
        ...figure.layout,
        margin: { l: 42, r: 12, t: 8, b: index === 0 ? 68 : 34 },
        paper_bgcolor: 'transparent',
        plot_bgcolor: 'transparent',
        hovermode: 'x unified',
        showlegend: index === 0
      }
    }));
  }

  private allLiveChartsReady(): boolean {
    return !!this.liveAllocationChart && !!this.liveValueChart && !!this.livePnlChart && !!this.livePriceChangeChart;
  }

  private allChartsReady(): boolean {
    return !!this.allocationChart && !!this.cumulativeChart && !!this.volatilityChart && !!this.drawdownChart;
  }

  private renderFigure(element: HTMLElement, figure: PlotlyFigure): void {
    void Plotly.newPlot(element, figure.data, {
      ...figure.layout,
      autosize: true,
      width: element.clientWidth,
      height: Math.max(320, element.clientHeight),
      title: null,
      margin: { l: 52, r: 18, t: 12, b: 48 },
      paper_bgcolor: 'transparent',
      plot_bgcolor: 'transparent',
      font: { color: '#b9c2d0', family: 'Inter, system-ui, sans-serif', size: 11 },
      hovermode: 'x unified',
      hoverlabel: { bgcolor: '#12161d', bordercolor: '#53658a', font: { color: '#f0f2f6', size: 11 } },
    }, { responsive: true, staticPlot: true, displayModeBar: false, displaylogo: false });
  }

  private getVolatilityWindow(): number {
    if (this.selectedPeriod === '1w') return 2;
    if (this.selectedPeriod === '1m') return 5;
    if (this.selectedPeriod === '6m') return 10;
    return this.volatilityWindow;
  }
}
