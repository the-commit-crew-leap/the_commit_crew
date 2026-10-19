import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthSessionService } from '../../../auth/auth-session.service';
import { InstrumentResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { WatchlistComponent } from './watchlist.component';

describe('WatchlistComponent', () => {
  let fixture: ComponentFixture<WatchlistComponent>;
  let component: WatchlistComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let tradingApi: jest.Mocked<TradingApiService>;

  const bitcoin: InstrumentResponse = {
    symbol: 'BTC',
    name: 'Bitcoin',
    assetClass: 'CRYPTO',
    currency: 'USD',
    tradable: true
  };
  const ethereum: InstrumentResponse = {
    symbol: 'ETH',
    name: 'Ethereum',
    assetClass: 'CRYPTO',
    currency: 'USD',
    tradable: true
  };

  beforeEach(async () => {
    authSession = {
      getAccountId: jest.fn().mockReturnValue('ACC-1001')
    } as unknown as jest.Mocked<AuthSessionService>;
    tradingApi = {
      getInstruments: jest.fn().mockReturnValue(of([bitcoin, ethereum])),
      getWatchlist: jest.fn().mockReturnValue(of([bitcoin])),
      getInstrumentPrice: jest.fn().mockReturnValue(of({
        symbol: 'BTC',
        price: 50000,
        source: 'ALPACA_IEX',
        live: true,
        updatedAt: '2026-10-19T10:00:00Z'
      })),
      addToWatchlist: jest.fn().mockReturnValue(of(ethereum)),
      removeFromWatchlist: jest.fn().mockReturnValue(of(void 0))
    } as unknown as jest.Mocked<TradingApiService>;

    await TestBed.configureTestingModule({
      imports: [WatchlistComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradingApiService, useValue: tradingApi }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WatchlistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads the watchlist and excludes already-added instruments', () => {
    expect(tradingApi.getInstruments).toHaveBeenCalledWith();
    expect(tradingApi.getWatchlist).toHaveBeenCalledWith('ACC-1001');
    expect(component.watchlist).toEqual([bitcoin]);
    expect(component.availableInstruments).toEqual([ethereum]);
    expect(tradingApi.getInstrumentPrice).toHaveBeenCalledWith('BTC');
    expect(component.getLatestPrice('BTC')).toBe(50000);
  });

  it('renders the styled instrument picker', () => {
    expect(fixture.nativeElement.querySelector('#instrument-search')).toBeNull();
    expect(fixture.nativeElement.querySelector('#instrument-select')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.instrument-picker')).not.toBeNull();

    component.openPicker();
    expect(component.pickerOpen).toBe(true);
    fixture.detectChanges();
    expect(component.pickerOpen).toBe(true);
    expect(fixture.nativeElement.querySelector('.instrument-options')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.instrument-option').textContent).toContain('ETH');
  });

  it('closes the picker when clicking outside the picker', () => {
    fixture.detectChanges();
    component.openPicker();
    fixture.detectChanges();

    component.closePickerOnOutsideClick(new MouseEvent('click', { bubbles: true }));

    expect(component.pickerOpen).toBe(false);
  });

  it('disables the add button until an instrument is selected', () => {
    fixture.detectChanges();

    const addButton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(addButton.disabled).toBe(true);

    const input = fixture.nativeElement.querySelector('#instrument-select') as HTMLInputElement;
    input.value = 'ETH';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const updatedAddButton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;

    expect(updatedAddButton.disabled).toBe(false);
  });

  it('displays asset and latest price columns', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Asset');
    expect(fixture.nativeElement.textContent).toContain('Latest price');
    expect(fixture.nativeElement.textContent).toContain('$50,000.00');
  });

  it('adds and removes an instrument for the current account', () => {
    component.selectInstrument(ethereum);
    component.addInstrument();

    expect(tradingApi.addToWatchlist).toHaveBeenCalledWith('ACC-1001', 'ETH');
    expect(component.watchlist).toEqual([bitcoin, ethereum]);

    component.removeInstrument(bitcoin);

    expect(tradingApi.removeFromWatchlist).toHaveBeenCalledWith('ACC-1001', 'BTC');
    expect(component.watchlist).toEqual([ethereum]);
  });

  it('shows an error when the initial watchlist load fails', () => {
    tradingApi.getWatchlist.mockReturnValue(throwError(() => new Error('failed')));

    const failedFixture = TestBed.createComponent(WatchlistComponent);
    const failedComponent = failedFixture.componentInstance;
    failedFixture.detectChanges();

    expect(failedComponent.error).toBe('Unable to load your watchlist.');
    expect(failedComponent.loading).toBe(false);
  });

  it('keeps the asset visible when its latest price cannot be loaded', () => {
    tradingApi.getInstrumentPrice.mockReturnValue(throwError(() => new Error('failed')));

    const failedPriceFixture = TestBed.createComponent(WatchlistComponent);
    const failedPriceComponent = failedPriceFixture.componentInstance;
    failedPriceFixture.detectChanges();

    expect(failedPriceComponent.watchlist).toEqual([bitcoin]);
    expect(failedPriceComponent.isPriceUnavailable('BTC')).toBe(true);
  });

  it('refreshes live prices every five seconds', () => {
    component.ngOnDestroy();
    jest.useFakeTimers();
    const priceRequests = tradingApi.getInstrumentPrice as jest.Mock;
    priceRequests.mockReset()
      .mockReturnValueOnce(of({
        symbol: 'BTC',
        price: 50000,
        source: 'ALPACA_IEX',
        live: true,
        updatedAt: '2026-10-19T10:00:00Z'
      }))
      .mockReturnValue(of({
        symbol: 'BTC',
        price: 51000,
        source: 'ALPACA_IEX',
        live: true,
        updatedAt: '2026-10-19T10:05:00Z'
      }));

    const refreshFixture = TestBed.createComponent(WatchlistComponent);
    const refreshComponent = refreshFixture.componentInstance;
    refreshComponent.ngOnInit();

    expect(refreshComponent.getLatestPrice('BTC')).toBe(50000);
    jest.advanceTimersByTime(5000);

    expect(refreshComponent.getLatestPrice('BTC')).toBe(51000);
    expect(refreshComponent.isPricePending('BTC')).toBe(false);
    expect(priceRequests).toHaveBeenCalledTimes(2);
    expect(refreshComponent.lastUpdated).not.toBeNull();
    jest.useRealTimers();
  });

  it('does not display a database fallback as a live price', () => {
    tradingApi.getInstrumentPrice.mockReturnValue(of({
      symbol: 'BTC',
      price: 121.26,
      source: 'DATABASE',
      live: false,
      updatedAt: '2021-02-26T00:00:00Z'
    }));

    const fallbackFixture = TestBed.createComponent(WatchlistComponent);
    const fallbackComponent = fallbackFixture.componentInstance;
    fallbackComponent.ngOnInit();

    expect(fallbackComponent.getLatestPrice('BTC')).toBeNull();
    expect(fallbackComponent.isPriceUnavailable('BTC')).toBe(true);
  });
});
