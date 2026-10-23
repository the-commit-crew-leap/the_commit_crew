import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth/auth-session.service';
import { InstrumentResponse, PositionResponse } from '../../api/trading.models';
import { TradingApiService } from '../../api/trading-api.service';
import { AnalyticsApiService } from '../../services/analytics-api.service';
import { PortfolioComponent } from './portfolio.component';

describe('PortfolioComponent', () => {
  let fixture: ComponentFixture<PortfolioComponent>;
  let component: PortfolioComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let tradingApi: jest.Mocked<TradingApiService>;
  let analyticsApi: jest.Mocked<AnalyticsApiService>;

  const positions: PositionResponse[] = [{
    symbol: 'AAPL',
    quantity: 10,
    averageCost: 100,
    currentPrice: 120,
    marketValue: 1200,
    unrealizedPnL: 200,
    unrealizedPnLPercent: 20,
    live: true
  }];
  const instruments: InstrumentResponse[] = [
    { symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    { symbol: 'MSFT', name: 'Microsoft', assetClass: 'EQUITY', currency: 'USD', tradable: true }
  ];
  const figure = { data: [], layout: {} };
  const statistic = {
    Ticker: 'AAPL',
    'Asset Class': 'EQUITY',
    Observations: 250,
    'Annualized Return (%)': 12,
    'Annualized Volatility (%)': 20,
    'Sharpe Ratio': 0.6
  };
  const scenario = {
    period: 'since_purchase',
    scenario: { symbol: 'MSFT', additional_quantity: 1, purchase_price: 100, purchase_value: 100 },
    current: { total_value: 1200, symbols: ['AAPL'], weights: [1], statistics: [statistic] },
    proposed: { total_value: 1300, symbols: ['AAPL', 'MSFT'], weights: [0.9, 0.1], statistics: [statistic] }
  };

  beforeEach(async () => {
    authSession = {
      getAccountId: jest.fn().mockReturnValue('ACC-3F842FBE')
    } as unknown as jest.Mocked<AuthSessionService>;
    tradingApi = {
      getPositions: jest.fn().mockReturnValue(of(positions)),
      getAccountOrders: jest.fn().mockReturnValue(of([
        {
          orderId: 'order-1',
          accountId: 'ACC-3F842FBE',
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 10,
          price: 90,
          status: 'FILLED',
          createdOn: '2024-05-01T12:00:00Z'
        }
      ])),
      getInstruments: jest.fn().mockReturnValue(of(instruments)),
      getBalance: jest.fn().mockReturnValue(of({
        accountId: 'ACC-3F842FBE',
        cashBalance: { amount: '500.00', currency: 'USD' }
      })),
      getInstrumentIntraday: jest.fn().mockReturnValue(of({
        symbol: 'AAPL',
        sessionDate: '2026-10-23',
        source: 'TEST',
        live: true,
        updatedAt: null,
        bars: [
          { timestamp: '2026-10-23T14:00:00Z', open: 110, high: 120, low: 110, close: 115, volume: 10 },
          { timestamp: '2026-10-23T14:01:00Z', open: 115, high: 125, low: 115, close: 120, volume: 10 }
        ]
      }))
    } as unknown as jest.Mocked<TradingApiService>;
    analyticsApi = {
      getPortfolioInsights: jest.fn().mockReturnValue(of({ period: 'since_purchase', symbols: ['AAPL'], weights: [1], statistics: [statistic] })),
      getPortfolioAllocation: jest.fn().mockReturnValue(of(figure)),
      getPortfolioCumulative: jest.fn().mockReturnValue(of(figure)),
      getPortfolioVolatility: jest.fn().mockReturnValue(of(figure)),
      getPortfolioDrawdown: jest.fn().mockReturnValue(of(figure)),
      getPortfolioScenario: jest.fn().mockReturnValue(of(scenario))
    } as unknown as jest.Mocked<AnalyticsApiService>;

    await TestBed.configureTestingModule({
      imports: [PortfolioComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradingApiService, useValue: tradingApi },
        { provide: AnalyticsApiService, useValue: analyticsApi }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PortfolioComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    component.ngOnDestroy();
    fixture.destroy();
  });

  it('loads the account portfolio and generates historical and live summaries', () => {
    component.ngOnInit();

    expect(component.positions).toEqual(positions);
    expect(component.orders).toHaveLength(1);
    expect(component.instruments).toEqual(instruments);
    expect(component.cashBalance).toBe(500);
    expect(component.scenarioSymbol).toBe('AAPL');
    expect(component.statistics).toEqual([statistic]);
    expect(component.loading).toBe(false);
    expect(component.livePortfolioValue()).toBe(1200);
    expect(component.liveTotalValue()).toBe(1700);
    expect(component.liveUnrealizedPnL()).toBe(200);
    expect(component.liveHasUnavailablePrices()).toBe(false);
    expect(analyticsApi.getPortfolioInsights).toHaveBeenCalledWith(
      ['AAPL'], [1], 'since_purchase', 30, ['2024-05-01']
    );
    expect(component.liveUpdatedAt).toBeInstanceOf(Date);
  });

  it('handles absent accounts, failed portfolio loads, and empty portfolios', () => {
    authSession.getAccountId.mockReturnValue(null);
    component.ngOnInit();
    expect(component.error).toBe('No account found in session.');
    expect(component.loading).toBe(false);
    expect(tradingApi.getPositions).not.toHaveBeenCalled();

    component.ngOnDestroy();
    authSession.getAccountId.mockReturnValue('ACC-3F842FBE');
    tradingApi.getPositions.mockReturnValue(throwError(() => new Error('offline')));
    const failedComponent = TestBed.createComponent(PortfolioComponent).componentInstance;
    failedComponent.ngOnInit();
    expect(failedComponent.error).toBe('Unable to load your holdings.');
    expect(failedComponent.loading).toBe(false);
    failedComponent.ngOnDestroy();

    component.positions = [];
    component.runPortfolioAnalytics();
    expect(component.error).toBe('Portfolio analytics are unavailable until market values load.');
  });

  it('rejects zero and unavailable holding values when calculating portfolio analytics', () => {
    component.positions = [
      { ...positions[0], symbol: 'ZERO', marketValue: 0, currentPrice: null },
      { ...positions[0], symbol: 'UNKNOWN', marketValue: null }
    ];

    component.runPortfolioAnalytics();
    component.runScenario();

    expect(component.error).toBe('Choose an instrument and enter a positive quantity.');
    expect(component.loading).toBe(false);
    expect(analyticsApi.getPortfolioInsights).not.toHaveBeenCalled();
    expect(analyticsApi.getPortfolioScenario).not.toHaveBeenCalled();
    expect(component.livePortfolioValue()).toBe(0);
    expect(component.liveHasUnavailablePrices()).toBe(true);
  });

  it('supports historical and live modes and portfolio calculations', () => {
    component.ngOnInit();
    component.setViewMode('historical');
    expect(component.viewMode).toBe('historical');
    expect(component.liveError).toBe('');
    component.setViewMode('historical');
    component.setViewMode('live');
    expect(component.viewMode).toBe('live');
    expect(component.formatWeight(0.125)).toBe('12.5%');
    expect(component.formatPercent(4.567)).toBe('4.57%');
    expect(component.formatPercent(undefined)).toBe('—');
    expect(component.formatRatio(1.236)).toBe('1.24');
    expect(component.formatRatio(null)).toBe('—');
    expect(component.scenarioMetric('current', 'Annualized Return (%)')).toBeUndefined();
    expect(component.liveHasUnavailablePrices()).toBe(false);

    component.positions = [{ ...positions[0], live: false }];
    expect(component.liveHasUnavailablePrices()).toBe(true);
  });

  it('validates and calculates portfolio scenarios', () => {
    component.ngOnInit();
    component.scenarioSymbol = '';
    component.runScenario();
    expect(component.error).toBe('Choose an instrument and enter a positive quantity.');
    component.scenarioSymbol = 'MSFT';
    component.scenarioQuantity = 1.5;
    component.runScenario();
    expect(component.scenarioLoading).toBe(false);

    component.scenarioQuantity = 2;
    component.runScenario();
    expect(analyticsApi.getPortfolioScenario).toHaveBeenCalledWith(
      ['AAPL'], [1200], 'MSFT', 2, 'since_purchase', 500, 30, ['2024-05-01']
    );
    expect(component.scenario).toEqual(scenario);
    expect(component.scenarioLoading).toBe(false);
    expect(component.scenarioMetric('proposed', 'Annualized Return (%)')).toBe(12);
  });

  it('reports scenario and historical analytics failures', () => {
    component.ngOnInit();
    analyticsApi.getPortfolioScenario.mockReturnValue(throwError(() => new Error('offline')));
    component.scenarioSymbol = 'MSFT';
    component.runScenario();
    expect(component.error).toBe('The portfolio scenario could not be calculated.');
    expect(component.scenarioLoading).toBe(false);

    analyticsApi.getPortfolioInsights.mockReturnValue(throwError(() => new Error('offline')));
    component.retryHistoricalAnalytics();
    expect(component.error).toBe('Portfolio analytics could not be generated.');
    expect(component.loading).toBe(false);
  });

  it('reports live refresh failures without discarding the existing portfolio snapshot', () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    tradingApi.getPositions
      .mockReturnValueOnce(of(positions))
      .mockReturnValueOnce(throwError(() => new Error('live refresh unavailable')));

    component.ngOnInit();

    expect(component.liveError).toBe('Live portfolio data is temporarily unavailable.');
    expect(component.liveLoading).toBe(false);
    expect(component.positions).toEqual(positions);
    expect(component.liveTotalValue()).toBe(1700);
    consoleError.mockRestore();
  });

  it('omits live-history points until intraday data is available for every held symbol', () => {
    const secondPosition: PositionResponse = {
      ...positions[0],
      symbol: 'MSFT',
      marketValue: 2000
    };
    tradingApi.getPositions.mockReturnValue(of([...positions, secondPosition]));
    tradingApi.getInstrumentIntraday.mockImplementation((symbol) =>
      symbol === 'AAPL'
        ? of({
          symbol: 'AAPL',
          sessionDate: '2026-10-23',
          source: 'TEST',
          live: true,
          updatedAt: null,
          bars: [{ timestamp: '2026-10-23T14:00:00Z', open: 110, high: 120, low: 110, close: 115, volume: 10 }]
        })
        : throwError(() => new Error('intraday data unavailable'))
    );
    component.ngOnInit();

    expect(component.liveError).toBe('');
    expect(component.liveUpdatedAt).toBeInstanceOf(Date);
    expect(component['liveHistory']).toEqual([]);
  });

  it('omits orders failures and falls back to the first held symbol', () => {
    tradingApi.getAccountOrders.mockReturnValue(throwError(() => new Error('orders unavailable')));
    tradingApi.getInstruments.mockReturnValue(of([]));
    const fallbackComponent = TestBed.createComponent(PortfolioComponent).componentInstance;
    fallbackComponent.ngOnInit();
    expect(fallbackComponent.orders).toEqual([]);
    expect(fallbackComponent.scenarioSymbol).toBe('AAPL');
    fallbackComponent.ngOnDestroy();
  });
});
