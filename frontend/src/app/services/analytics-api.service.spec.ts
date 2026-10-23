import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../environments/environment';
import { AnalyticsApiService } from './analytics-api.service';

describe('AnalyticsApiService', () => {
  let service: AnalyticsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AnalyticsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests instrument insights and charts with repeated tickers', () => {
    service.getInsights(['AAPL', 'MSFT'], '1y', 30).subscribe();
    service.getVolatility(['AAPL', 'MSFT'], '1y', 30).subscribe();
    service.getDrawdown(['AAPL', 'MSFT'], '1y').subscribe();

    const insights = http.expectOne((request) => request.url === `${environment.analyticsApiBaseUrl}/insights`);
    expect(insights.request.params.getAll('tickers')).toEqual(['AAPL', 'MSFT']);
    expect(insights.request.params.get('period')).toBe('1y');
    expect(insights.request.params.get('volatility_window')).toBe('30');
    insights.flush({});

    const volatility = http.expectOne((request) => request.url.endsWith('/charts/volatility'));
    expect(volatility.request.params.getAll('tickers')).toEqual(['AAPL', 'MSFT']);
    expect(volatility.request.params.get('volatility_window')).toBe('30');
    volatility.flush({});

    const drawdown = http.expectOne((request) => request.url.endsWith('/charts/drawdown'));
    expect(drawdown.request.params.getAll('tickers')).toEqual(['AAPL', 'MSFT']);
    expect(drawdown.request.params.has('volatility_window')).toBe(false);
    drawdown.flush({});
  });

  it('requests a single-symbol candlestick chart', () => {
    service.getCandlestick('BRK.B', '6m').subscribe();
    const request = http.expectOne((candidate) => candidate.url.endsWith('/charts/candlestick'));

    expect(request.request.params.get('ticker')).toBe('BRK.B');
    expect(request.request.params.get('period')).toBe('6m');
    request.flush({});
  });

  it('sends portfolio symbols, weights, acquisition dates, and optional windows', () => {
    service.getPortfolioInsights(['AAPL', 'MSFT'], [0.6, 0.4], 'since_purchase', 20, ['2024-01-01']).subscribe();
    service.getPortfolioCumulative(['AAPL'], [1], 'since_purchase', ['2024-01-01']).subscribe();
    service.getPortfolioVolatility(['AAPL'], [1], '1y', 15).subscribe();
    service.getPortfolioDrawdown(['AAPL'], [1], '1y').subscribe();
    service.getPortfolioAllocation(['AAPL', 'CASH'], [0.8, 0.2]).subscribe();

    const insights = http.expectOne((request) => request.url.endsWith('/portfolio/insights'));
    expect(insights.request.params.getAll('symbols')).toEqual(['AAPL', 'MSFT']);
    expect(insights.request.params.getAll('weights')).toEqual(['0.6', '0.4']);
    expect(insights.request.params.get('volatility_window')).toBe('20');
    expect(insights.request.params.getAll('acquisition_dates')).toEqual(['2024-01-01']);
    insights.flush({});

    const cumulative = http.expectOne((request) => request.url.endsWith('/portfolio/charts/cumulative'));
    expect(cumulative.request.params.get('volatility_window')).toBeNull();
    expect(cumulative.request.params.getAll('acquisition_dates')).toEqual(['2024-01-01']);
    cumulative.flush({});

    const volatility = http.expectOne((request) => request.url.endsWith('/portfolio/charts/volatility'));
    expect(volatility.request.params.get('volatility_window')).toBe('15');
    volatility.flush({});

    const drawdown = http.expectOne((request) => request.url.endsWith('/portfolio/charts/drawdown'));
    expect(drawdown.request.params.has('volatility_window')).toBe(false);
    drawdown.flush({});

    const allocation = http.expectOne((request) => request.url.endsWith('/portfolio/charts/allocation'));
    expect(allocation.request.params.get('period')).toBe('1y');
    expect(allocation.request.params.getAll('symbols')).toEqual(['AAPL', 'CASH']);
    allocation.flush({});
  });

  it('sends portfolio scenario parameters and repeated values', () => {
    service.getPortfolioScenario(
      ['AAPL', 'MSFT'], [1000, 500], 'NVDA', 2, 'since_purchase', 250, 30, ['2023-01-01', '2024-01-01']
    ).subscribe();
    const request = http.expectOne((candidate) => candidate.url.endsWith('/portfolio/scenario'));

    expect(request.request.params.getAll('symbols')).toEqual(['AAPL', 'MSFT']);
    expect(request.request.params.getAll('values')).toEqual(['1000', '500']);
    expect(request.request.params.get('scenario_symbol')).toBe('NVDA');
    expect(request.request.params.get('additional_quantity')).toBe('2');
    expect(request.request.params.get('cash_value')).toBe('250');
    expect(request.request.params.get('volatility_window')).toBe('30');
    expect(request.request.params.getAll('acquisition_dates')).toEqual(['2023-01-01', '2024-01-01']);
    request.flush({});
  });
});
