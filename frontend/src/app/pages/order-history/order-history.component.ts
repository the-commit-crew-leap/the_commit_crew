import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Order, OrderStatus } from '../../models/order.model';
import { OrderHistoryService } from '../../services/order-history.service';
import { AuthSessionService } from '../../auth/auth-session.service';

@Component({
  selector: 'app-order-history',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './order-history.html',
  styleUrl: './order-history.css'
})
export class OrderHistoryComponent implements OnInit {
  user = this.authSession.getCurrentUser();
  loading = true;
  error = '';
  orders: Order[] = [];

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

    this.orderHistoryService.getOrderHistory(accountId).subscribe({
      next: (orders) => {
        this.orders = [...orders].sort((a, b) =>
          new Date(b.createdOn).getTime() - new Date(a.createdOn).getTime()
        );
        this.loading = false;
        this.changeDetectorRef.detectChanges();
      },
      error: (error) => {
        this.error = 'Could not load order history.';
        this.loading = false;
        this.changeDetectorRef.detectChanges();
      }
    });
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

  private getAccountId(): string | null {
    return this.authSession.getAccountId();
  }
}