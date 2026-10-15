import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthSessionService } from '../../auth/auth-session.service';
import { TradingApiService } from '../../api/trading-api.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let tradingApi: jest.Mocked<TradingApiService>;

  beforeEach(async () => {
    authSession = {
      getAccountId: jest.fn().mockReturnValue('ACC-1001')
    } as unknown as jest.Mocked<AuthSessionService>;
    tradingApi = {
      getBalance: jest.fn().mockReturnValue(of({
        accountId: 'ACC-1001',
        cashBalance: { amount: '10000.00', currency: 'USD' }
      })),
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
      ]))
    } as unknown as jest.Mocked<TradingApiService>;

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradingApiService, useValue: tradingApi },
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
    expect(component.balance?.cashBalance.amount).toBe('10000.00');
  });
});
