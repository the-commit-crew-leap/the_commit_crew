import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthSessionService } from '../../../auth/auth-session.service';
import { TradingApiService } from '../../../api/trading-api.service';
import { AssetsComponent } from './assets.component';

describe('AssetsComponent', () => {
  let fixture: ComponentFixture<AssetsComponent>;
  let component: AssetsComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let tradingApi: jest.Mocked<TradingApiService>;

  beforeEach(async () => {
    authSession = {
      getAccountId: jest.fn().mockReturnValue('ACC-1001')
    } as unknown as jest.Mocked<AuthSessionService>;
    tradingApi = {
      getPositions: jest.fn().mockReturnValue(of([]))
    } as unknown as jest.Mocked<TradingApiService>;

    await TestBed.configureTestingModule({
      imports: [AssetsComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradingApiService, useValue: tradingApi }
      ]
    }).compileComponents();
  });

  it('loads positions and formats values', () => {
    tradingApi.getPositions.mockReturnValue(of([{
      symbol: 'BTC',
      quantity: 0.42,
      averageCost: 60000,
      currentPrice: 67432.8,
      marketValue: 28321.78,
      unrealizedPnL: 3121.78,
      unrealizedPnLPercent: 12.4
    }]));

    fixture = TestBed.createComponent(AssetsComponent);
    component = fixture.componentInstance;
    component.ngOnInit();

    expect(tradingApi.getPositions).toHaveBeenCalledWith('ACC-1001');
    expect(component.positions[0].symbol).toBe('BTC');
    expect(component.formatCurrency(67432.8)).toBe('67,432.80');
    expect(component.formatQuantity(0.42)).toBe('0.42');
    expect(component.loading).toBe(false);
  });

  it('exposes an error when positions fail to load', () => {
    tradingApi.getPositions.mockReturnValue(throwError(() => new Error('failed')));

    fixture = TestBed.createComponent(AssetsComponent);
    component = fixture.componentInstance;
    component.ngOnInit();

    expect(component.error).toBe('Live prices are temporarily unavailable.');
    expect(component.loading).toBe(false);
  });

  it('handles a missing account session', () => {
    authSession.getAccountId.mockReturnValue(null);

    fixture = TestBed.createComponent(AssetsComponent);
    component = fixture.componentInstance;
    component.ngOnInit();

    expect(component.error).toBe('No account found in session. Please log in again.');
    expect(tradingApi.getPositions).not.toHaveBeenCalled();
  });
});
