import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthSessionService } from '../../auth/auth-session.service';
import { TradingApiService } from '../../api/trading-api.service';
import { DepositService } from '../../services/deposit.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let tradingApi: jest.Mocked<TradingApiService>;
  let depositService: {
    balance: ReturnType<typeof signal>;
    getBalance: jest.Mock;
  };

  beforeEach(async () => {
    authSession = {
      getAccountId: jest.fn().mockReturnValue('ACC-1001'),
      username: signal('alice')
    } as unknown as jest.Mocked<AuthSessionService>;
    tradingApi = {
      getPositions: jest.fn().mockReturnValue(of([{
        symbol: 'BTC',
        quantity: 1,
        averageCost: 60000,
        currentPrice: 65000,
        marketValue: 65000,
        unrealizedPnL: 5000,
        unrealizedPnLPercent: 8.33
      }])),
      getAccountOrders: jest.fn().mockReturnValue(of([
        {
          orderId: 'order-1',
          accountId: 'ACC-1001',
          symbol: 'BTC',
          side: 'BUY',
          quantity: 1,
          price: 60000,
          status: 'NEW',
          createdOn: '2026-10-15T08:00:00'
        },
        {
          orderId: 'order-2',
          accountId: 'ACC-1001',
          symbol: 'ETH',
          side: 'BUY',
          quantity: 1,
          price: 2000,
          status: 'FILLED',
          createdOn: '2026-10-15T07:00:00'
        }
      ])),
      getInstruments: jest.fn().mockReturnValue(of([])),
      getWatchlist: jest.fn().mockReturnValue(of([])),
      getInstrumentPrice: jest.fn().mockReturnValue(of({
        symbol: 'AAPL',
        price: 100,
        source: 'ALPACA_IEX',
        live: true,
        updatedAt: '2026-10-19T10:00:00Z'
      }))
    } as unknown as jest.Mocked<TradingApiService>;
    depositService = {
      balance: signal({
        accountId: 'ACC-1001',
        cashBalance: { amount: '10000.00', currency: 'USD' }
      }),
      getBalance: jest.fn().mockReturnValue(of({
        accountId: 'ACC-1001',
        cashBalance: { amount: '10000.00', currency: 'USD' }
      }))
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradingApiService, useValue: tradingApi },
        { provide: DepositService, useValue: depositService },
        { provide: Router, useValue: { navigate: jest.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
  });

  it('calculates portfolio overview values', () => {
    expect(component.portfolioValue).toBe(65000);
    expect(component.todayReturn).toBe(5000);
    expect(component.openOrders).toBe(1);
    expect(component.balance()?.cashBalance.amount).toBe('10000.00');
  });

  it('personalizes the greeting with the signed-in username', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.welcome-message').textContent)
      .toContain("Welcome back, alice. Here's what's in your account.");
  });

  it('falls back to a generic greeting when no username is available', () => {
    authSession.username.set(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.welcome-message').textContent)
      .toContain("Welcome back. Here's what's in your account.");
  });

  it('updates portfolio value when the live position price changes', () => {
    component.ngOnDestroy();
    jest.useFakeTimers();
    tradingApi.getPositions.mockReset()
      .mockReturnValueOnce(of([{
        symbol: 'AAPL',
        quantity: 50,
        averageCost: 100,
        currentPrice: 100,
        marketValue: 5000,
        unrealizedPnL: 0,
        unrealizedPnLPercent: 0
      }]))
      .mockReturnValue(of([{
        symbol: 'AAPL',
        quantity: 50,
        averageCost: 100,
        currentPrice: 110,
        marketValue: 5500,
        unrealizedPnL: 500,
        unrealizedPnLPercent: 10
      }]));

    const refreshFixture = TestBed.createComponent(DashboardComponent);
    const refreshComponent = refreshFixture.componentInstance;
    refreshComponent.ngOnInit();

    expect(refreshComponent.portfolioValue).toBe(5000);
    jest.advanceTimersByTime(5000);

    expect(refreshComponent.portfolioValue).toBe(5500);
    expect(refreshComponent.todayReturn).toBe(500);
    jest.useRealTimers();
  });

  it('marks a negative today return as negative', () => {
    component.ngOnDestroy();
    tradingApi.getPositions.mockReturnValue(of([{
      symbol: 'AAPL',
      quantity: 1,
      averageCost: 100,
      currentPrice: 90,
      marketValue: 90,
      unrealizedPnL: -10,
      unrealizedPnLPercent: -10
    }]));

    const negativeFixture = TestBed.createComponent(DashboardComponent);
    const negativeComponent = negativeFixture.componentInstance;
    negativeComponent.ngOnInit();
    negativeFixture.detectChanges();

    const returnValues = negativeFixture.nativeElement.querySelectorAll('.overview-card strong');
    expect(returnValues[2].className).toContain('negative');
    expect(returnValues[2].className).not.toContain('positive');
    negativeComponent.ngOnDestroy();
  });
});
