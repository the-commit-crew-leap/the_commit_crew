import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { MarketTickerResponse } from '../api/trading.models';
import { TradingApiService } from '../api/trading-api.service';
import { MarketTickerComponent } from './market-ticker.component';

describe('MarketTickerComponent', () => {
  let fixture: ComponentFixture<MarketTickerComponent>;
  let component: MarketTickerComponent;
  let tradingApi: jest.Mocked<TradingApiService>;

  const markets: MarketTickerResponse[] = [
    { symbol: 'BTC/USD', price: 67432.8, changePercent: 2.34, source: 'ALPACA_CRYPTO', live: true, updatedAt: '2026-10-19T13:00:00Z' },
    { symbol: 'ETH/USD', price: 2648.12, changePercent: 1.86, source: 'ALPACA_CRYPTO', live: true, updatedAt: '2026-10-19T13:00:00Z' },
    { symbol: 'SOL/USD', price: 154.62, changePercent: -0.72, source: 'ALPACA_CRYPTO', live: true, updatedAt: '2026-10-19T13:00:00Z' },
    { symbol: 'AVAX/USD', price: 28.43, changePercent: 3.12, source: 'ALPACA_CRYPTO', live: true, updatedAt: '2026-10-19T13:00:00Z' }
  ];

  beforeEach(async () => {
    tradingApi = {
      getMarketTicker: jest.fn().mockReturnValue(of(markets))
    } as unknown as jest.Mocked<TradingApiService>;

    await TestBed.configureTestingModule({
      imports: [MarketTickerComponent],
      providers: [{ provide: TradingApiService, useValue: tradingApi }]
    }).compileComponents();

    fixture = TestBed.createComponent(MarketTickerComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    component.ngOnDestroy();
    jest.useRealTimers();
  });

  it('renders the four markets and their changes', () => {
    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('BTC/USD');
    expect(fixture.nativeElement.textContent).toContain('67,432.80');
    expect(fixture.nativeElement.textContent).toContain('+2.34%');
    expect(fixture.nativeElement.textContent).toContain('−0.72%');
    expect(fixture.nativeElement.textContent).toContain('Crypto markets open 24/7');
  });

  it('refreshes every five seconds without moving the ticker', () => {
    jest.useFakeTimers();
    tradingApi.getMarketTicker
      .mockReturnValueOnce(of(markets))
      .mockReturnValue(of(markets.map((market) => ({ ...market, price: market.price === null ? null : market.price + 1 }))));

    component.ngOnInit();
    expect(tradingApi.getMarketTicker).toHaveBeenCalledTimes(1);

    jest.advanceTimersByTime(5000);

    expect(tradingApi.getMarketTicker).toHaveBeenCalledTimes(2);
  });

  it('shows unavailable values when the backend request fails', () => {
    tradingApi.getMarketTicker.mockReturnValue(throwError(() => new Error('offline')));

    component.ngOnInit();
    fixture.detectChanges();

    expect(component.error).toBe('Market data is temporarily unavailable.');
    expect(fixture.nativeElement.textContent).toContain('—');
  });

  it('does not display unavailable values as live prices', () => {
    tradingApi.getMarketTicker.mockReturnValue(of([
      { ...markets[0], price: null, changePercent: null, live: false, source: null, updatedAt: null },
      ...markets.slice(1)
    ]));

    component.ngOnInit();

    expect(component.marketData[0].live).toBe(false);
    expect(component.marketData[0].price).toBeNull();
  });
});
