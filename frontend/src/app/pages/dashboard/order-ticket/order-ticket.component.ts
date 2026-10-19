import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthSessionService } from '../../../auth/auth-session.service';
import { TradingApiService } from '../../../api/trading-api.service';
import {
  BalanceResponse,
  InstrumentResponse,
  OrderResponse,
  OrderSide
} from '../../../api/trading.models';

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-ticket.component.html',
  styleUrl: './order-ticket.component.css'
})
export class OrderTicketComponent implements OnInit {
  @Input() balance: BalanceResponse | null = null;
  instruments: InstrumentResponse[] = [];
  instrumentsLoading = true;
  instrumentsError = '';
  symbol = '';
  side: OrderSide = 'BUY';
  quantity: number | null = null;
  marketPrice: number | null = null;
  priceLoading = false;
  priceError = '';
  orderSubmitting = false;
  orderError = '';
  submittedOrder: OrderResponse | null = null;
  validationSubmitted = false;
  symbolInvalid = false;
  quantityInvalid = false;
  instrumentPickerOpen = false;
  highlightedInstrumentIndex = 0;
  @ViewChild('symbolInput') private symbolInput?: ElementRef<HTMLInputElement>;
  @ViewChild('quantityInput') private quantityInput?: ElementRef<HTMLInputElement>;
  @ViewChild('orderErrorElement') private orderErrorElement?: ElementRef<HTMLElement>;
  private pricedSymbol = '';
  private priceRequestToken = 0;

  constructor(
    private readonly authSession: AuthSessionService,
    private readonly tradingApi: TradingApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.tradingApi.getInstruments().subscribe({
      next: (instruments) => {
        this.instruments = instruments.filter((instrument) => instrument.tradable);
        this.instrumentsLoading = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to load tradable instruments', error);
        this.instrumentsError = 'Unable to load tradable instruments.';
        this.instrumentsLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  placeOrder(): void {
    const accountId = this.authSession.getAccountId();
    const normalizedSymbol = this.symbol.trim().toUpperCase();
    const selectedInstrument = this.instruments.find(
      (instrument) => instrument.symbol === normalizedSymbol
    );

    this.validationSubmitted = true;
    this.symbolInvalid = !selectedInstrument;
    this.quantityInvalid =
      !this.quantity || this.quantity <= 0 || !Number.isInteger(this.quantity);
    this.orderError = '';
    this.priceError = '';
    this.submittedOrder = null;

    if (!accountId) {
      this.orderError = 'Your session has expired. Please log in again.';
      this.focusOrderError();
      return;
    }

    if (!normalizedSymbol) {
      this.orderError = 'Enter a symbol.';
      this.cdr.detectChanges();
      this.focusFirstInvalidField();
      return;
    }

    if (!selectedInstrument) {
      this.orderError = 'Select a valid instrument from the list.';
      this.cdr.detectChanges();
      this.focusFirstInvalidField();
      return;
    }

    if (!this.quantity || this.quantity <= 0 || !Number.isInteger(this.quantity)) {
      this.orderError = 'Quantity must be a positive whole number.';
      this.cdr.detectChanges();
      this.focusFirstInvalidField();
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
        this.focusOrderError();
      }
    });
  }

  onSymbolChange(value: string): void {
    this.instrumentPickerOpen = true;
    this.highlightedInstrumentIndex = 0;
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

  get filteredInstruments(): InstrumentResponse[] {
    const query = this.symbol.trim().toLowerCase();
    if (!query) {
      return this.instruments;
    }

    return this.instruments.filter((instrument) =>
      instrument.symbol.toLowerCase().includes(query) ||
      instrument.name.toLowerCase().includes(query)
    );
  }

  openInstrumentPicker(): void {
    if (!this.instrumentsLoading && !this.instrumentsError) {
      this.instrumentPickerOpen = true;
    }
  }

  closeInstrumentPicker(): void {
    setTimeout(() => {
      this.instrumentPickerOpen = false;
    }, 150);
  }

  onInstrumentKeydown(event: KeyboardEvent): void {
    if (!this.instrumentPickerOpen && event.key !== 'ArrowDown') {
      return;
    }

    const instruments = this.filteredInstruments;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.instrumentPickerOpen = true;
      this.highlightedInstrumentIndex = Math.min(
        this.highlightedInstrumentIndex + 1,
        Math.max(instruments.length - 1, 0)
      );
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.highlightedInstrumentIndex = Math.max(this.highlightedInstrumentIndex - 1, 0);
    } else if (event.key === 'Enter' && instruments[this.highlightedInstrumentIndex]) {
      event.preventDefault();
      this.selectInstrument(instruments[this.highlightedInstrumentIndex]);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.instrumentPickerOpen = false;
    }
  }

  selectInstrument(instrument: InstrumentResponse): void {
    this.symbol = instrument.symbol;
    this.symbolInvalid = false;
    this.highlightedInstrumentIndex = 0;
    this.onSymbolChange(this.symbol);
    this.instrumentPickerOpen = false;
    this.loadMarketPrice();
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

  get symbolError(): string {
    if (!this.symbolInvalid) {
      return '';
    }

    return this.symbol.trim()
      ? 'Select a valid instrument from the list.'
      : 'Enter an asset symbol.';
  }

  get quantityError(): string {
    if (this.quantityInvalid && (this.quantity === null || this.quantity === undefined)) {
      return 'Enter a quantity.';
    }

    return this.quantityInvalid
      ? 'Quantity must be a positive whole number.'
      : '';
  }

  private focusFirstInvalidField(): void {
    setTimeout(() => {
      if (this.symbolError) {
        this.symbolInput?.nativeElement.focus();
      } else if (this.quantityError) {
        this.quantityInput?.nativeElement.focus();
      }
    });
  }

  private focusOrderError(): void {
    setTimeout(() => this.orderErrorElement?.nativeElement.focus());
  }
}
