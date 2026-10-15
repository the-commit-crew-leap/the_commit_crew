import { ChangeDetectorRef } from '@angular/core';
import { OrderHistoryService } from '../../services/order-history.service';
import { AuthSessionService } from '../../auth/auth-session.service';
import { OrderHistoryComponent } from './order-history.component';

describe('OrderHistoryComponent', () => {
  it('calculates the total value from quantity and price', () => {
    const component = new OrderHistoryComponent(
      {} as OrderHistoryService,
      {} as AuthSessionService,
      {} as ChangeDetectorRef
    );

    expect(component.orderTotal({
      orderId: 'order-1',
      accountId: 'ACC-1001',
      symbol: 'BTC',
      side: 'BUY',
      quantity: 3,
      price: 125.5,
      status: 'FILLED',
      createdOn: '2026-10-15T08:00:00'
    })).toBe(376.5);
  });
});
