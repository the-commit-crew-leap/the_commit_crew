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
      getInstrumentPrice: jest.fn().mockReturnValue(of({ symbol: 'BTC', price: 50000 })),
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
    component.ngOnInit();
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
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#instrument-search')).toBeNull();
    expect(fixture.nativeElement.querySelector('#instrument-select')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.instrument-picker')).not.toBeNull();

    component.pickerOpen = true;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.instrument-options')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.instrument-option').textContent).toContain('ETH');
  });

  it('disables the add button until an instrument is selected', () => {
    fixture.detectChanges();

    const addButton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(addButton.disabled).toBe(true);

    component.selectedSymbol = 'ETH';
    fixture.detectChanges();

    expect(addButton.disabled).toBe(false);
  });

  it('displays asset and latest price columns', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Asset');
    expect(fixture.nativeElement.textContent).toContain('Latest price');
    expect(fixture.nativeElement.textContent).toContain('$50,000.00');
  });

  it('adds and removes an instrument for the current account', () => {
    component.selectedSymbol = 'ETH';
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
    failedComponent.ngOnInit();

    expect(failedComponent.error).toBe('Unable to load your watchlist.');
    expect(failedComponent.loading).toBe(false);
  });

  it('keeps the asset visible when its latest price cannot be loaded', () => {
    tradingApi.getInstrumentPrice.mockReturnValue(throwError(() => new Error('failed')));

    const failedPriceFixture = TestBed.createComponent(WatchlistComponent);
    const failedPriceComponent = failedPriceFixture.componentInstance;
    failedPriceComponent.ngOnInit();

    expect(failedPriceComponent.watchlist).toEqual([bitcoin]);
    expect(failedPriceComponent.isPriceUnavailable('BTC')).toBe(true);
  });
});
