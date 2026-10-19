import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AssetClass, Order, OrderSide, OrderStatus } from '../../models/order.model';
import { OrderHistoryService } from '../../services/order-history.service';
import { AuthSessionService } from '../../auth/auth-session.service';
import { Subject, interval, startWith, switchMap, takeUntil } from 'rxjs';

@Component({
  selector: 'app-order-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './order-history.html',
  styleUrl: './order-history.css'
})
export class OrderHistoryComponent implements OnDestroy, OnInit {
  loading = true;
  error = '';
  orders: Order[] = [];
  selectedAssetClass: AssetClass | '' = '';
  selectedSide: OrderSide | '' = '';
  selectedStatus: OrderStatus | '' = '';
  readonly assetClasses: AssetClass[] = ['EQUITY', 'BOND', 'FUND', 'CASH'];
  readonly sides: OrderSide[] = ['BUY', 'SELL'];
  readonly statuses: OrderStatus[] = ['NEW', 'FILLED', 'REJECTED', 'CANCELLED'];
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly orderHistoryService: OrderHistoryService,
    private readonly authSession: AuthSessionService,
    private readonly changeDetectorRef: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading = true;
    this.error = '';

    const accountId = this.getAccountId();
    if (!accountId) {
      this.error = 'No account found in session. Please log in again.';
      this.loading = false;
      this.changeDetectorRef.detectChanges();
      return;
    }

    interval(30000).pipe(
      startWith(0),
      switchMap(() => this.orderHistoryService.getOrderHistory(accountId)),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (orders) => {
        this.orders = [...orders].sort((a, b) =>
          new Date(b.createdOn).getTime() - new Date(a.createdOn).getTime()
        );
        this.loading = false;
        this.changeDetectorRef.detectChanges();
      },
      error: (error) => {
        console.error('Failed to refresh order history', error);
        this.error = 'Could not load order history.';
        this.loading = false;
        this.changeDetectorRef.detectChanges();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  statusClass(status: OrderStatus): string {
    switch (status) {
      case 'FILLED':
        return 'badge badge-filled';
      case 'NEW':
        return 'badge badge-new';
      case 'REJECTED':
        return 'badge badge-rejected';
      case 'CANCELLED':
        return 'badge badge-cancelled';
      default:
        return 'badge';
    }
  }

  formatCreatedOn(value: string): string {
    return new Date(value).toLocaleString();
  }

  get totalOrderValue(): number {
    return this.orders.reduce((total, order) => total + this.orderValue(order), 0);
  }

  get filteredOrders(): Order[] {
    return this.orders.filter((order) =>
      (!this.selectedAssetClass || order.assetClass === this.selectedAssetClass) &&
      (!this.selectedSide || order.side === this.selectedSide) &&
      (!this.selectedStatus || order.status === this.selectedStatus)
    );
  }

  get filteredOrderValue(): number {
    return this.filteredOrders.reduce((total, order) => total + this.orderValue(order), 0);
  }

  get filledOrders(): number {
    return this.orders.filter((order) => order.status === 'FILLED').length;
  }

  get openOrders(): number {
    return this.orders.filter((order) => order.status === 'NEW').length;
  }

  get cancelledOrders(): number {
    return this.orders.filter((order) => order.status === 'CANCELLED').length;
  }

  orderValue(order: Order): number {
    return order.quantity * order.price;
  }

  formatCurrency(value: number): string {
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  orderTotal(order: Order): number {
    return order.quantity * order.price;
  }

  private getAccountId(): string | null {
    return this.authSession.getAccountId();
  }
}