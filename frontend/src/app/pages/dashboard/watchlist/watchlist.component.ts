import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  OnDestroy,
  ElementRef,
  HostListener,
  OnInit,
  ViewChild,
  inject
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, Subject, TimeoutError, catchError, exhaustMap, forkJoin, interval, map, of, startWith, takeUntil, tap, timeout } from 'rxjs';

import { AuthSessionService } from '../../../auth/auth-session.service';
import { InstrumentResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { isUsMarketOpen } from '../../../shared/market-status';

@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './watchlist.component.html',
  styleUrl: './watchlist.component.css'
})
export class WatchlistComponent implements OnDestroy, OnInit {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradingApi = inject(TradingApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  @ViewChild('instrumentPicker') private instrumentPicker?: ElementRef<HTMLElement>;

  instruments: InstrumentResponse[] = [];
  watchlist: InstrumentResponse[] = [];
  watchlistPrices: Record<string, number | null> = {};
  selectedSymbol = '';
  loading = true;
  priceLoading = false;
  priceError = '';
  lastUpdated: Date | null = null;
  lastTradeAt: Date | null = null;
  saving = false;
  removingSymbol = '';
  error = '';
  success = '';
  pickerOpen = false;
  highlightedInstrumentIndex = 0;

  @HostListener('document:click', ['$event'])
  closePickerOnOutsideClick(event: MouseEvent): void {
    if (!this.instrumentPicker?.nativeElement.contains(event.target as Node)) {
      this.pickerOpen = false;
    }
  }

  ngOnInit(): void {
    const accountId = this.authSession.getAccountId();
    if (!accountId) {
      this.error = 'No account found in session. Please log in again.';
      this.loading = false;
      return;
    }

    forkJoin({
      instruments: this.tradingApi.getInstruments(),
      watchlist: this.tradingApi.getWatchlist(accountId)
    }).subscribe({
      next: ({ instruments, watchlist }) => {
        this.instruments = instruments;
        this.watchlist = watchlist;
        this.startPriceRefresh();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to load watchlist data', error);
        this.error = 'Unable to load your watchlist.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get availableInstruments(): InstrumentResponse[] {
    const symbols = new Set(this.watchlist.map((instrument) => instrument.symbol));
    return this.instruments.filter((instrument) => !symbols.has(instrument.symbol));
  }

  get filteredAvailableInstruments(): InstrumentResponse[] {
    const query = this.selectedSymbol.trim().toLowerCase();
    if (!query) {
      return this.availableInstruments;
    }

    return this.availableInstruments.filter((instrument) =>
      instrument.symbol.toLowerCase().includes(query) ||
      instrument.name.toLowerCase().includes(query)
    );
  }

  openPicker(): void {
    if (!this.loading && !this.error && !this.saving) {
      this.pickerOpen = true;
    }
  }

  closePicker(): void {
    setTimeout(() => {
      this.pickerOpen = false;
    }, 150);
  }

  onPickerKeydown(event: KeyboardEvent): void {
    const instruments = this.filteredAvailableInstruments;
    if (!this.pickerOpen && event.key !== 'ArrowDown') {
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.pickerOpen = true;
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
      this.pickerOpen = false;
    }
  }

  selectInstrument(instrument: InstrumentResponse): void {
    this.selectedSymbol = instrument.symbol;
    this.highlightedInstrumentIndex = 0;
    this.pickerOpen = false;
  }

  addInstrument(): void {
    const accountId = this.authSession.getAccountId();
    const selectedInstrument = this.availableInstruments.find(
      (instrument) => instrument.symbol === this.selectedSymbol.trim().toUpperCase()
    );
    if (!accountId || !selectedInstrument || this.saving) {
      if (accountId && this.selectedSymbol && !selectedInstrument) {
        this.error = 'Select a valid instrument from the list.';
      }
      return;
    }

    this.saving = true;
    this.error = '';
    this.success = '';
    const symbol = this.selectedSymbol;
    this.tradingApi.addToWatchlist(accountId, this.selectedSymbol).pipe(
      timeout({ first: 10000 })
    ).subscribe({
      next: (instrument) => {
        this.watchlist = [...this.watchlist, instrument];
        this.refreshPricesNow();
        this.selectedSymbol = '';
        this.saving = false;
        this.success = `${instrument.symbol} was added to your watchlist.`;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to add instrument to watchlist', error);
        if (error instanceof TimeoutError) {
          this.reconcileTimedOutAdd(accountId, symbol);
          return;
        }

        this.error = 'Unable to add that instrument to your watchlist.';
        this.saving = false;
        this.cdr.detectChanges();
      }
    }).add(() => {
      if (!this.saving) {
        return;
      }
      this.saving = false;
      this.cdr.detectChanges();
    });
  }

  onSelectedSymbolChange(value: string): void {
    this.selectedSymbol = value;
    this.highlightedInstrumentIndex = 0;
    this.pickerOpen = true;
  }

  private reconcileTimedOutAdd(accountId: string, symbol: string): void {
    this.tradingApi.getWatchlist(accountId).subscribe({
      next: (watchlist) => {
        const instrument = watchlist.find((item) => item.symbol === symbol);
        if (instrument) {
          this.watchlist = watchlist;
          this.refreshPricesNow();
          this.selectedSymbol = '';
          this.success = `${instrument.symbol} was added to your watchlist.`;
        } else {
          this.error = 'The instrument could not be confirmed in your watchlist.';
        }
        this.saving = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to confirm the watchlist update', error);
        this.error = 'The instrument may have been added, but it could not be confirmed. Refresh and try again.';
        this.saving = false;
        this.cdr.detectChanges();
      }
    });
  }

  removeInstrument(instrument: InstrumentResponse): void {
    const accountId = this.authSession.getAccountId();
    if (!accountId || this.removingSymbol) {
      return;
    }

    this.removingSymbol = instrument.symbol;
    this.error = '';
    this.success = '';
    this.tradingApi.removeFromWatchlist(accountId, instrument.symbol).subscribe({
      next: () => {
        this.watchlist = this.watchlist.filter((item) => item.symbol !== instrument.symbol);
        const prices = { ...this.watchlistPrices };
        delete prices[instrument.symbol];
        this.watchlistPrices = prices;
        this.removingSymbol = '';
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to remove instrument from watchlist', error);
        this.error = 'Unable to remove that instrument from your watchlist.';
        this.removingSymbol = '';
        this.cdr.detectChanges();
      }
    });
  }

  getLatestPrice(symbol: string): number | null {
    return this.watchlistPrices[symbol] ?? null;
  }

  isPricePending(symbol: string): boolean {
    return this.priceLoading && !(symbol in this.watchlistPrices);
  }

  isPriceUnavailable(symbol: string): boolean {
    return symbol in this.watchlistPrices && this.watchlistPrices[symbol] === null;
  }

  private startPriceRefresh(): void {
    interval(5000).pipe(
      startWith(0),
      exhaustMap(() => this.fetchWatchlistPrices()),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (prices) => this.applyPrices(prices),
      error: (error) => {
        console.error('Watchlist price refresh stopped unexpectedly', error);
        this.priceLoading = false;
        this.priceError = 'Live prices are temporarily unavailable.';
        this.cdr.detectChanges();
      }
    });
  }

  private refreshPricesNow(): void {
    this.fetchWatchlistPrices().pipe(takeUntil(this.destroy$)).subscribe({
      next: (prices) => this.applyPrices(prices),
      error: (error) => {
        console.error('Failed to refresh watchlist prices', error);
        this.priceLoading = false;
        this.priceError = 'Live prices are temporarily unavailable.';
        this.cdr.detectChanges();
      }
    });
  }

  private fetchWatchlistPrices(): Observable<Record<string, number | null>> {
    const watchlist = [...this.watchlist];
    this.priceError = '';
    this.priceLoading = watchlist.length > 0;
    this.cdr.detectChanges();

    if (watchlist.length === 0) {
      return of({});
    }

    return forkJoin(watchlist.map((instrument) =>
      this.tradingApi.getInstrumentPrice(instrument.symbol).pipe(
        tap((response) => this.recordLastTradeAt(response.updatedAt)),
        map((response) => response.live ? response.price : null),
        catchError((error) => {
          console.error(`Failed to load live price for ${instrument.symbol}`, error);
          return of(null);
        })
      )
    )).pipe(
      map((prices) => prices.reduce<Record<string, number | null>>((result, price, index) => {
        result[watchlist[index].symbol] = price;
        return result;
      }, {}))
    );
  }

  private applyPrices(prices: Record<string, number | null>): void {
    this.watchlistPrices = prices;
    this.priceLoading = false;
    this.lastUpdated = new Date();
    if (Object.values(prices).some((price) => price === null)) {
      this.priceError = 'Some live prices are currently unavailable.';
    }
    this.cdr.detectChanges();
  }

  get marketOpen(): boolean {
    return isUsMarketOpen();
  }

  private recordLastTradeAt(timestamp: string): void {
    const tradeTime = new Date(timestamp).getTime();
    if (!Number.isFinite(tradeTime)
      || (this.lastTradeAt !== null && this.lastTradeAt.getTime() >= tradeTime)) {
      return;
    }
    this.lastTradeAt = new Date(tradeTime);
  }
}
