import { CommonModule } from '@angular/common';
import { AfterViewChecked, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Subject, takeUntil } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { InstrumentResponse, PositionResponse } from '../../api/trading.models';
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

  readonly periods = [
    { value: '1w', label: '1W' }, { value: '1m', label: '1M' }, { value: '6m', label: '6M' },
    { value: '1y', label: '1Y' }, { value: '5y', label: '5Y' }, { value: '10y', label: '10Y' }
  ];
  positions: PositionResponse[] = [];
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
    forkJoin({
      positions: this.tradingApi.getPositions(accountId),
      instruments: this.tradingApi.getInstruments(true),
      balance: this.tradingApi.getBalance(accountId)
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ positions, instruments, balance }) => {
        this.positions = positions;
        this.instruments = instruments;
        this.cashBalance = Number(balance.cashBalance.amount) || 0;
        this.scenarioSymbol = instruments[0]?.symbol ?? this.positions[0]?.symbol ?? '';
        this.runPortfolioAnalytics();
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
  }

  ngOnDestroy(): void {
    [this.allocationChart, this.cumulativeChart, this.volatilityChart, this.drawdownChart]
      .forEach((chart) => { if (chart) Plotly.purge(chart.nativeElement); });
    this.destroy$.next();
    this.destroy$.complete();
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
    if (!symbols.length || !this.scenarioSymbol || this.scenarioQuantity <= 0 || !Number.isInteger(this.scenarioQuantity)) {
      this.error = 'Choose an instrument and enter a positive quantity.';
      return;
    }
    this.scenarioLoading = true;
    this.error = '';
    this.analyticsApi.getPortfolioScenario(
      symbols, values, this.scenarioSymbol, this.scenarioQuantity,
      this.selectedPeriod, this.cashBalance, this.getVolatilityWindow()
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
    const allocationTotal = total + this.cashBalance;
    const allocationSymbols = [...symbols, 'CASH'];
    const allocationWeights = [...values, this.cashBalance].map((value) => value / allocationTotal);
    const window = this.getVolatilityWindow();
    this.loading = true;
    this.error = '';
    forkJoin({
      insights: this.analyticsApi.getPortfolioInsights(symbols, weights, this.selectedPeriod, window),
      allocation: this.analyticsApi.getPortfolioAllocation(allocationSymbols, allocationWeights),
      cumulative: this.analyticsApi.getPortfolioCumulative(symbols, weights, this.selectedPeriod),
      volatility: this.analyticsApi.getPortfolioVolatility(symbols, weights, this.selectedPeriod, window),
      drawdown: this.analyticsApi.getPortfolioDrawdown(symbols, weights, this.selectedPeriod)
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
      font: { color: '#dbe2ed' },
      hovermode: 'closest'
    }, { responsive: true, staticPlot: true, displayModeBar: false, displaylogo: false });
  }

  private getVolatilityWindow(): number {
    if (this.selectedPeriod === '1w') return 2;
    if (this.selectedPeriod === '1m') return 5;
    if (this.selectedPeriod === '6m') return 10;
    return this.volatilityWindow;
  }
}
