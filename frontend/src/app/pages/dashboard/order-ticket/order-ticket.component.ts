import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthSessionService } from '../../../auth/auth-session.service';
import { TradingApiService } from '../../../api/trading-api.service';
import { BalanceResponse, OrderResponse, OrderSide } from '../../../api/trading.models';

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-ticket.component.html',
  styleUrl: './order-ticket.component.css'
})
export class OrderTicketComponent {
  @Input() balance: BalanceResponse | null = null;
  symbol = '';
  side: OrderSide = 'BUY';
  quantity: number | null = null;
  orderSubmitting = false;
  orderError = '';
  submittedOrder: OrderResponse | null = null;

  constructor(
    private readonly authSession: AuthSessionService,
    private readonly tradingApi: TradingApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  placeOrder(): void {
    const accountId = this.authSession.getAccountId();
    const normalizedSymbol = this.symbol.trim().toUpperCase();

    this.orderError = '';
    this.submittedOrder = null;

    if (!accountId) {
      this.orderError = 'Your session has expired. Please log in again.';
      return;
    }

    if (!normalizedSymbol) {
      this.orderError = 'Enter a symbol.';
      return;
    }

    if (!this.quantity || this.quantity <= 0 || !Number.isInteger(this.quantity)) {
      this.orderError = 'Quantity must be a positive whole number.';
      return;
    }

    this.orderSubmitting = true;
    this.tradingApi.placeOrder({
      accountId,
      symbol: normalizedSymbol,
      side: this.side,
      quantity: this.quantity
    }).subscribe({
      next: (order) => {
        this.submittedOrder = order;
        this.symbol = '';
        this.quantity = null;
        this.orderSubmitting = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to place order', error);
        this.orderError = 'Could not place the order. Please review the details and try again.';
        this.orderSubmitting = false;
        this.cdr.detectChanges();
      }
    });
  }
}
