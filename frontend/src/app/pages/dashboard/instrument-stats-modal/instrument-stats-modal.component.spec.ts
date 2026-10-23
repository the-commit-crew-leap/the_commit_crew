import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { TradingApiService } from '../../../api/trading-api.service';
import { InstrumentStatsModalComponent } from './instrument-stats-modal.component';

describe('InstrumentStatsModalComponent', () => {
  let fixture: ComponentFixture<InstrumentStatsModalComponent>;
  let component: InstrumentStatsModalComponent;
  let tradingApi: jest.Mocked<TradingApiService>;

  const intraday = {
    symbol: 'AAPL',
    sessionDate: '2020-01-02',
    source: 'TEST',
    live: false,
    updatedAt: null,
    bars: [
      { timestamp: '2020-01-02T14:00:00Z', open: 100, high: 110, low: 95, close: 105, volume: 10 },
      { timestamp: '2020-01-02T14:01:00Z', open: 105, high: 115, low: 90, close: 102, volume: 20 }
    ]
  };

  beforeEach(async () => {
    tradingApi = {
      getInstrumentPrice: jest.fn().mockReturnValue(of({
        symbol: 'AAPL', price: 102, source: 'TEST', live: false, updatedAt: '2020-01-02T14:01:00Z'
      })),
      getInstrumentIntraday: jest.fn().mockReturnValue(of(intraday)),
      getInstrumentFundamentals: jest.fn().mockReturnValue(of(null))
    } as unknown as jest.Mocked<TradingApiService>;

    await TestBed.configureTestingModule({
      imports: [InstrumentStatsModalComponent],
      providers: [
        { provide: TradingApiService, useValue: tradingApi },
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InstrumentStatsModalComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    component.ngOnDestroy();
    fixture.destroy();
  });

  it('loads market data and exposes safe session statistics', () => {
    component.symbol = 'AAPL';
    component.visible = true;
    component.ngOnChanges({
      symbol: new SimpleChange('', 'AAPL', true),
      visible: new SimpleChange(false, true, true)
    });

    expect(tradingApi.getInstrumentPrice).toHaveBeenCalledWith('AAPL');
    expect(component.quote?.price).toBe(102);
    expect(component.sessionOpen()).toBe(100);
    expect(component.sessionHigh()).toBe(115);
    expect(component.sessionLow()).toBe(90);
    expect(component.sessionClose()).toBe(102);
    expect(component.openToCloseDifference()).toBe(2);
    expect(component.formatDifference(2)).toBe('+$2.00');
    expect(component.formatDifference(-2)).toBe('-$2.00');
    expect(component.formatDifference(null)).toBe('—');
    expect(component.formatPrice(undefined)).toBe('—');
    expect(component.sessionLabel()).toBe('Last available session');
    expect(component.isLastAvailableSession()).toBe(true);
  });

  it('handles empty bars and missing fundamentals without throwing', () => {
    tradingApi.getInstrumentIntraday.mockReturnValue(of({ ...intraday, bars: [] }));
    tradingApi.getInstrumentFundamentals.mockReturnValue(throwError(() => new Error('not available')));
    component.symbol = 'AAPL';
    component.visible = true;
    component.ngOnChanges({
      symbol: new SimpleChange('', 'AAPL', true),
      visible: new SimpleChange(false, true, true)
    });

    expect(component.sessionHigh()).toBeNull();
    expect(component.sessionLow()).toBeNull();
    expect(component.sessionOpen()).toBeNull();
    expect(component.sessionClose()).toBeNull();
    expect(component.openToCloseDifference()).toBeNull();
    expect(component.fundamentals).toBeNull();
    expect(component.fundamentalsError).toBe('SEC fundamentals are unavailable for this instrument.');
    expect(component.error).toBe('');
  });

  it('uses an initial position as the quote and reports market-data failures', () => {
    component.initialPosition = {
      symbol: 'AAPL',
      quantity: 1,
      averageCost: 90,
      currentPrice: 100,
      marketValue: 100,
      unrealizedPnL: 10,
      unrealizedPnLPercent: 11,
      priceSource: null,
      priceUpdatedAt: null
    };
    component.ngOnChanges({
      initialPosition: new SimpleChange(undefined, component.initialPosition, true)
    });
    expect(component.quote?.price).toBe(100);
    expect(component.quote?.source).toBe('dashboard');

    tradingApi.getInstrumentPrice.mockReturnValue(throwError(() => new Error('offline')));
    component.symbol = 'AAPL';
    component.visible = true;
    component.ngOnChanges({
      symbol: new SimpleChange('', 'AAPL', true),
      visible: new SimpleChange(false, true, true)
    });
    expect(component.error).toBe('Statistics are temporarily unavailable.');
    expect(component.loading).toBe(false);
  });

  it('emits close requests and changes tabs', () => {
    const closed = jest.spyOn(component.closed, 'emit');
    component.close();
    component.setTab('fundamentals');
    expect(closed).toHaveBeenCalled();
    expect(component.activeTab).toBe('fundamentals');
  });
});
