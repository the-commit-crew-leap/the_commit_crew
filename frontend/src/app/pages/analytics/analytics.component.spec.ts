import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { FundamentalsResponse, InstrumentResponse } from '../../api/trading.models';
import { TradingApiService } from '../../api/trading-api.service';
import { AnalyticsApiService } from '../../services/analytics-api.service';
import { AnalyticsComponent } from './analytics.component';

describe('AnalyticsComponent', () => {
  let fixture: ComponentFixture<AnalyticsComponent>;
  let component: AnalyticsComponent;
  let tradingApi: jest.Mocked<TradingApiService>;
  let analyticsApi: jest.Mocked<AnalyticsApiService>;
  let routeSymbol: string | null;

  const instruments: InstrumentResponse[] = [
    { symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    { symbol: 'BTC', name: 'Bitcoin', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
    { symbol: 'MSFT', name: 'Microsoft', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    { symbol: 'SPY', name: 'S&P 500', assetClass: 'FUND', currency: 'USD', tradable: true }
  ];
  const statistic = {
    Ticker: 'AAPL',
    'Asset Class': 'EQUITY',
    Observations: 250,
    'Annualized Return (%)': 12,
    'Annualized Volatility (%)': 20,
    'Sharpe Ratio': 0.6,
    'Maximum Drawdown (%)': -15
  };
  const insights = {
    metadata: { generated_at: '2026-01-01', period: '1y', tickers: ['AAPL'], ticker_count: 1 },
    statistics: [statistic],
    insights: {
      best_return: { ticker: 'AAPL', value: 12 },
      worst_return: null,
      highest_volatility: { ticker: 'AAPL', value: 20 },
      lowest_volatility: null,
      best_sharpe: { ticker: 'AAPL', value: 0.6 },
      risk_ranking: []
    }
  };
  const figure = { data: [], layout: {} };
  const fundamentals: FundamentalsResponse = {
    symbol: 'AAPL',
    companyName: 'Apple',
    revenue: 1000,
    netIncome: 200,
    earningsPerShare: 5,
    totalAssets: 1500,
    totalLiabilities: 500,
    cashAndEquivalents: 100,
    operatingCashFlow: 250,
    sharesOutstanding: 100,
    filedAt: '2026-01-01',
    fiscalPeriod: 'FY'
  };

  beforeEach(async () => {
    routeSymbol = null;
    tradingApi = {
      getInstruments: jest.fn().mockReturnValue(of(instruments)),
      getInstrumentFundamentals: jest.fn().mockReturnValue(of(fundamentals)),
      getInstrumentPrice: jest.fn().mockReturnValue(of({ price: 150 }))
    } as unknown as jest.Mocked<TradingApiService>;
    analyticsApi = {
      getInsights: jest.fn().mockReturnValue(of(insights)),
      getVolatility: jest.fn().mockReturnValue(of(figure)),
      getDrawdown: jest.fn().mockReturnValue(of(figure)),
      getCandlestick: jest.fn().mockReturnValue(of(figure))
    } as unknown as jest.Mocked<AnalyticsApiService>;

    await TestBed.configureTestingModule({
      imports: [AnalyticsComponent],
      providers: [
        { provide: TradingApiService, useValue: tradingApi },
        { provide: AnalyticsApiService, useValue: analyticsApi },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => routeSymbol } } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsComponent);
    component = fixture.componentInstance;
  });

  it('loads instruments and runs analytics for the default selection', () => {
    component.ngOnInit();

    expect(component.loadingInstruments).toBe(false);
    expect(component.selectedSymbols).toEqual(['AAPL', 'BTC', 'MSFT']);
    expect(component.statistics).toEqual([statistic]);
    expect(component.fundamentals).toEqual([fundamentals, fundamentals, fundamentals]);
    expect(component.fundamentalsPrices).toEqual({ AAPL: 150, BTC: 150, MSFT: 150 });
    expect(analyticsApi.getCandlestick).not.toHaveBeenCalled();
    expect(component.fundamentalsError).toBe('');
  });

  it('selects the requested symbol from the route and requests its candlestick', () => {
    routeSymbol = ' btc ';
    component.ngOnInit();

    expect(component.selectedSymbols).toEqual(['BTC']);
    expect(analyticsApi.getCandlestick).toHaveBeenCalledWith('BTC', '1y');
  });

  it('falls back to the default symbols when the route requests an unknown instrument', () => {
    routeSymbol = 'UNKNOWN';
    component.ngOnInit();

    expect(component.selectedSymbols).toEqual(['AAPL', 'BTC', 'MSFT']);
    expect(analyticsApi.getCandlestick).not.toHaveBeenCalled();
  });

  it('exposes instrument loading errors', () => {
    tradingApi.getInstruments.mockReturnValue(throwError(() => new Error('offline')));
    component.ngOnInit();

    expect(component.loadingInstruments).toBe(false);
    expect(component.error).toBe('Unable to load the instrument universe.');
  });

  it('filters instruments and selection by asset class and supports toggling symbols', () => {
    component.instruments = instruments;
    component.selectedSymbols = ['AAPL', 'BTC'];

    expect(component.assetClasses).toEqual(['CRYPTO', 'EQUITY', 'FUND']);
    component.setAssetClass('EQUITY');
    expect(component.visibleInstruments.map(({ symbol }) => symbol)).toEqual(['AAPL', 'MSFT']);
    expect(component.selectedSymbols).toEqual(['AAPL']);

    component.toggleSymbol('MSFT');
    component.toggleSymbol('AAPL');
    expect(component.selectedSymbols).toEqual(['MSFT']);
    component.setAssetClass('ALL');
    expect(component.visibleInstruments).toEqual(instruments);
  });

  it('requires a selection and applies period-specific volatility windows', () => {
    component.selectedSymbols = [];
    component.runAnalytics();
    expect(component.error).toBe('Select at least one instrument.');

    component.selectedSymbols = ['AAPL'];
    component.period = '1w';
    component.volatilityWindow = 30;
    component.runAnalytics();
    expect(analyticsApi.getInsights).toHaveBeenLastCalledWith(['AAPL'], '1w', 2);
    component.period = '1m';
    component.runAnalytics();
    expect(analyticsApi.getInsights).toHaveBeenLastCalledWith(['AAPL'], '1m', 5);
    component.period = '6m';
    component.runAnalytics();
    expect(analyticsApi.getInsights).toHaveBeenLastCalledWith(['AAPL'], '6m', 10);
    component.period = '5y';
    component.runAnalytics();
    expect(analyticsApi.getInsights).toHaveBeenLastCalledWith(['AAPL'], '5y', 30);
  });

  it('handles analytics errors and missing company fundamentals', () => {
    component.selectedSymbols = ['AAPL'];
    analyticsApi.getInsights.mockReturnValue(throwError(() => new Error('offline')));
    component.runAnalytics();
    expect(component.error).toBe('Analytics could not be generated for the selected instruments.');
    expect(component.loadingAnalytics).toBe(false);

    analyticsApi.getInsights.mockReturnValue(of(insights));
    tradingApi.getInstrumentFundamentals.mockReturnValue(of(null as unknown as FundamentalsResponse));
    component.runAnalytics();
    expect(component.fundamentals).toEqual([]);
    expect(component.fundamentalsError).toBe('SEC fundamentals are unavailable for the selected instruments.');
  });

  it('keeps available fundamentals and maps failed or missing prices to null', () => {
    component.selectedSymbols = ['AAPL', 'BTC'];
    tradingApi.getInstrumentFundamentals.mockImplementation((symbol) =>
      symbol === 'AAPL' ? of(fundamentals) : throwError(() => new Error('not reported'))
    );
    tradingApi.getInstrumentPrice.mockImplementation((symbol) =>
      symbol === 'AAPL'
        ? throwError(() => new Error('price unavailable'))
        : of({ symbol: 'BTC', price: 42, source: 'TEST', live: true, updatedAt: '2026-01-01T00:00:00Z' })
    );
    component.runAnalytics();

    expect(component.fundamentals).toEqual([fundamentals]);
    expect(component.fundamentalsError).toBe('');
    expect(component.fundamentalsPrices).toEqual({ AAPL: null, BTC: 42 });
    expect(component.priceToEarnings(fundamentals)).toBeNull();
  });

  it('formats statistics and computes fundamentals ratios with invalid-data guards', () => {
    expect(component.formatPercent(12.345)).toBe('12.35%');
    expect(component.formatPercent(null)).toBe('—');
    expect(component.formatRatio(1.236)).toBe('1.24');
    expect(component.formatRatio(null)).toBe('—');

    component.fundamentalsPrices = { AAPL: 150 };
    expect(component.priceToEarnings(fundamentals)).toBe(30);
    expect(component.returnOnEquity(fundamentals)).toBe(20);
    expect(component.debtToEquity(fundamentals)).toBe(0.5);
    expect(component.formatFundamentalRatio(2)).toBe('2.00');
    expect(component.formatFundamentalRatio(Number.NaN)).toBe('—');
    expect(component.formatFundamentalPercent(2)).toBe('2.00%');

    const noEquity = { ...fundamentals, totalAssets: 500, totalLiabilities: 500 };
    expect(component.returnOnEquity(noEquity)).toBeNull();
    expect(component.debtToEquity(noEquity)).toBeNull();
    expect(component.priceToEarnings({ ...fundamentals, earningsPerShare: 0 })).toBeNull();
    expect(component.formatFundamentalPercent(Number.POSITIVE_INFINITY)).toBe('—');
  });

  it('cleans up subscriptions on destruction', () => {
    component.ngOnDestroy();
    expect(() => fixture.destroy()).not.toThrow();
  });
});
