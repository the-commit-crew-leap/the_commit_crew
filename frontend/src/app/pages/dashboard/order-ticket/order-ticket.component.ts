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
  marketPrice: number | null = null;
  priceLoading = false;
  priceError = '';
  orderSubmitting = false;
  orderError = '';
  submittedOrder: OrderResponse | null = null;
  private pricedSymbol = '';
  private priceRequestToken = 0;

  constructor(
    private readonly authSession: AuthSessionService,
    private readonly tradingApi: TradingApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  placeOrder(): void {
    const accountId = this.authSession.getAccountId();
    const normalizedSymbol = this.symbol.trim().toUpperCase();

    this.orderError = '';
    this.priceError = '';
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
        this.marketPrice = null;
        this.priceLoading = false;
        this.pricedSymbol = '';
        this.priceRequestToken++;
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

  onSymbolChange(value: string): void {
    const normalizedSymbol = value.trim().toUpperCase();
    if (normalizedSymbol === this.pricedSymbol) {
      return;
    }

    this.marketPrice = null;
    this.priceError = '';
    this.priceLoading = false;
    this.pricedSymbol = '';
    this.priceRequestToken++;
  }

  loadMarketPrice(): void {
    const normalizedSymbol = this.symbol.trim().toUpperCase();
    if (!normalizedSymbol || (normalizedSymbol === this.pricedSymbol && this.marketPrice !== null)) {
      return;
    }

    const requestToken = ++this.priceRequestToken;
    this.priceLoading = true;
    this.priceError = '';
    this.tradingApi.getInstrumentPrice(normalizedSymbol).subscribe({
      next: (response) => {
        if (requestToken !== this.priceRequestToken) {
          return;
        }

        this.marketPrice = response.price;
        this.pricedSymbol = response.symbol;
        this.priceLoading = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        if (requestToken !== this.priceRequestToken) {
          return;
        }

        console.error('Failed to load instrument price', error);
        this.marketPrice = null;
        this.pricedSymbol = '';
        this.priceLoading = false;
        this.priceError = 'Unable to load the current price for this instrument.';
        this.cdr.detectChanges();
      }
    });
  }

  get estimatedOrderValue(): number | null {
    if (this.marketPrice === null || this.quantity === null || this.quantity <= 0) {
      return null;
    }

    return this.marketPrice * this.quantity;
  }

  getOrderTotal(quantity: number, price: number): number {
    return quantity * price;
  }
}
