import { ChangeDetectorRef } from '@angular/core';
import { OrderHistoryService } from '../../services/order-history.service';
import { AuthSessionService } from '../../auth/auth-session.service';
import { OrderHistoryComponent } from './order-history.component';

describe('OrderHistoryComponent', () => {
  function createComponent(): OrderHistoryComponent {
    return new OrderHistoryComponent(
      {} as OrderHistoryService,
      {} as AuthSessionService,
      {} as ChangeDetectorRef
    );
  }

  it('calculates the total value from quantity and price', () => {
    const component = createComponent();

    expect(component.orderTotal({
      orderId: 'order-1',
      accountId: 'ACC-1001',
      symbol: 'BTC',
      side: 'BUY',
      quantity: 3,
      price: 125.5,
      status: 'FILLED',
      createdOn: '2026-10-15T08:00:00',
      assetClass: 'EQUITY'
    })).toBe(376.5);
  });

  it('calculates the total price from the execution price when available', () => {
    const component = createComponent();

    expect(component.orderTotal({
      orderId: 'order-1',
      accountId: 'ACC-1001',
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 3,
      price: 125.5,
      executionPrice: 124.25,
      status: 'FILLED',
      createdOn: '2026-10-15T08:00:00',
      assetClass: 'EQUITY'
    })).toBe(372.75);
  });

  it('filters orders by asset class, side, and status', () => {
    const component = createComponent();
    component.orders = [
      {
        orderId: 'order-1',
        accountId: 'ACC-1001',
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 3,
        price: 125.5,
        status: 'FILLED',
        createdOn: '2026-10-15T08:00:00',
        assetClass: 'EQUITY'
      },
      {
        orderId: 'order-2',
        accountId: 'ACC-1001',
        symbol: 'BND',
        side: 'SELL',
        quantity: 2,
        price: 100,
        status: 'NEW',
        createdOn: '2026-10-15T07:00:00',
        assetClass: 'BOND'
      },
      {
        orderId: 'order-3',
        accountId: 'ACC-1001',
        symbol: 'FUND',
        side: 'BUY',
        quantity: 1,
        price: 80,
        status: 'FILLED',
        createdOn: '2026-10-15T06:00:00',
        assetClass: 'FUND'
      }
    ];

    component.selectedAssetClass = 'EQUITY';
    component.selectedSide = 'BUY';
    component.selectedStatus = 'FILLED';

    expect(component.filteredOrders.map((order) => order.orderId)).toEqual(['order-1']);
    expect(component.filteredOrderValue).toBe(376.5);
  });

  it('returns no orders when a filter has no matches', () => {
    const component = createComponent();
    component.orders = [{
      orderId: 'order-1',
      accountId: 'ACC-1001',
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 3,
      price: 125.5,
      status: 'FILLED',
      createdOn: '2026-10-15T08:00:00',
      assetClass: 'EQUITY'
    }];
    component.selectedStatus = 'CANCELLED';

    expect(component.filteredOrders).toEqual([]);
  });

  it('clears an individual active filter', () => {
    const component = createComponent();
    component.selectedAssetClass = 'EQUITY';
    component.selectedSide = 'BUY';
    component.selectedStatus = 'FILLED';

    component.clearAssetClassFilter();
    expect(component.selectedAssetClass).toBe('');
    expect(component.selectedSide).toBe('BUY');
    expect(component.selectedStatus).toBe('FILLED');

    component.clearSideFilter();
    expect(component.selectedSide).toBe('');

    component.clearStatusFilter();
    expect(component.selectedStatus).toBe('');
  });

  it('does not offer CASH as an asset class filter', () => {
    const component = createComponent();

    expect(component.assetClasses).toEqual(['EQUITY', 'BOND', 'FUND']);
  });
});
