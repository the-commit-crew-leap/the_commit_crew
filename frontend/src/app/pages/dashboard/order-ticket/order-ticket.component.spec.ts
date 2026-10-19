import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ChangeDetectorRef } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthSessionService } from '../../../auth/auth-session.service';
import { InstrumentResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { OrderTicketComponent } from './order-ticket.component';

describe('OrderTicketComponent', () => {
  let fixture: ComponentFixture<OrderTicketComponent>;
  let component: OrderTicketComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let tradingApi: jest.Mocked<TradingApiService>;
  const bitcoin: InstrumentResponse = {
    symbol: 'BTC',
    name: 'Bitcoin',
    assetClass: 'CRYPTO',
    currency: 'USD',
    tradable: true
  };

  beforeEach(async () => {
    authSession = {
      getAccountId: jest.fn().mockReturnValue('ACC-1001')
    } as unknown as jest.Mocked<AuthSessionService>;
    tradingApi = {
      placeOrder: jest.fn(),
      getInstrumentPrice: jest.fn(),
      getInstruments: jest.fn().mockReturnValue(of([bitcoin]))
    } as unknown as jest.Mocked<TradingApiService>;

    await TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradingApiService, useValue: tradingApi }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderTicketComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
  });

  it('rejects a missing symbol and invalid quantity', () => {
    component.symbol = ' ';
    component.quantity = 1.5;

    component.placeOrder();

    expect(component.orderError).toBe('Enter a symbol.');
    expect(tradingApi.placeOrder).not.toHaveBeenCalled();

    component.symbol = 'BTC';
    component.quantity = 0;
    component.placeOrder();

    expect(component.orderError).toBe('Quantity must be a positive whole number.');
    expect(tradingApi.placeOrder).not.toHaveBeenCalled();
  });

  it('marks invalid fields and focuses the first invalid field', fakeAsync(() => {
    fixture.detectChanges();

    component.symbol = '';
    component.quantity = 0;
    fixture.detectChanges();
    component.placeOrder();
    fixture.detectChanges();
    tick();

    const symbolInput = fixture.nativeElement.querySelector('#ticket-symbol');
    expect(symbolInput.getAttribute('aria-invalid')).toBe('true');
    expect(symbolInput.getAttribute('aria-describedby')).toBe('ticket-symbol-error');
    expect(document.activeElement).toBe(symbolInput);
  }));

  it('focuses the quantity field when the symbol is valid', fakeAsync(() => {
    fixture.detectChanges();

    component.symbol = 'BTC';
    component.quantity = 0;
    fixture.detectChanges();
    component.placeOrder();
    fixture.detectChanges();
    tick();

    const quantityInput = fixture.nativeElement.querySelector('#ticket-quantity');
    expect(quantityInput.getAttribute('aria-invalid')).toBe('true');
    expect(quantityInput.getAttribute('aria-describedby')).toBe('ticket-quantity-error');
    expect(document.activeElement).toBe(quantityInput);
  }));

  it('normalizes the symbol and submits the order', () => {
    const response = {
      orderId: 'order-1',
      accountId: 'ACC-1001',
      symbol: 'BTC',
      side: 'BUY' as const,
      quantity: 2,
      price: 50000,
      status: 'NEW' as const,
      createdOn: '2026-10-15T08:00:00'
    };
    tradingApi.placeOrder.mockReturnValue(of(response));
    component.symbol = ' btc ';
    component.quantity = 2;

    component.placeOrder();

    expect(tradingApi.placeOrder).toHaveBeenCalledWith({
      accountId: 'ACC-1001',
      symbol: 'BTC',
      side: 'BUY',
      quantity: 2
    });
    expect(component.submittedOrder).toEqual(response);
    expect(component.symbol).toBe('');
    expect(component.quantity).toBeNull();
    expect(component.orderSubmitting).toBe(false);
  });

  it('rejects a symbol that is not in the available instruments', () => {
    component.symbol = 'UNKNOWN';
    component.quantity = 2;

    component.placeOrder();

    expect(component.orderError).toBe('Select a valid instrument from the list.');
    expect(tradingApi.placeOrder).not.toHaveBeenCalled();
  });

  it('renders available instruments in the styled picker', () => {
    fixture.detectChanges();

    const symbolInput = fixture.nativeElement.querySelector('#ticket-symbol');
    symbolInput.dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.instrument-options')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.instrument-option strong').textContent).toContain('BTC');
  });

  it('shows an error when order submission fails', () => {
    tradingApi.placeOrder.mockReturnValue(throwError(() => new Error('failed')));
    component.symbol = 'BTC';
    component.quantity = 2;

    component.placeOrder();

    expect(component.orderError).toBe(
      'Could not place the order. Please review the details and try again.'
    );
    expect(component.orderSubmitting).toBe(false);
  });

  it('loads the current price and calculates the estimated order value', () => {
    tradingApi.getInstrumentPrice.mockReturnValue(of({
      symbol: 'BTC',
      price: 50000
    }));
    component.symbol = 'btc';
    component.quantity = 2;

    component.onSymbolChange(component.symbol);
    component.loadMarketPrice();

    expect(tradingApi.getInstrumentPrice).toHaveBeenCalledWith('BTC');
    expect(component.marketPrice).toBe(50000);
    expect(component.estimatedOrderValue).toBe(100000);
    expect(component.priceLoading).toBe(false);
  });

  it('shows a price error when the current price cannot be loaded', () => {
    tradingApi.getInstrumentPrice.mockReturnValue(throwError(() => new Error('failed')));
    component.symbol = 'UNKNOWN';

    component.loadMarketPrice();

    expect(component.marketPrice).toBeNull();
    expect(component.priceError).toBe(
      'Unable to load the current price for this instrument.'
    );
    expect(component.priceLoading).toBe(false);
  });

  it('rejects submission when the session has no account', () => {
    authSession.getAccountId.mockReturnValue(null);
    component.symbol = 'BTC';
    component.quantity = 2;

    component.placeOrder();

    expect(component.orderError).toBe('Your session has expired. Please log in again.');
    expect(tradingApi.placeOrder).not.toHaveBeenCalled();
  });
});
