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
import { InstrumentResponse, IntradayPriceResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { MarketDataService } from '../../../services/market-data.service';

interface WatchlistDetails {
  price: number;
  changePercent: number | null;
  high: number;
  low: number;
  live: boolean;
  bars: number[];
}

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
  private readonly marketData = inject(MarketDataService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();
  private successTimeout?: ReturnType<typeof setTimeout>;

  @ViewChild('instrumentPicker') private instrumentPicker?: ElementRef<HTMLElement>;

  instruments: InstrumentResponse[] = [];
  watchlist: InstrumentResponse[] = [];
  watchlistPrices: Record<string, number | null> = {};
  watchlistDetails: Record<string, WatchlistDetails | null> = {};
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
  watchlistFilter = '';
  sortMode: 'symbol' | 'change' | 'price' = 'symbol';

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
    this.clearSuccessTimeout();
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

  get displayedWatchlist(): InstrumentResponse[] {
    const query = this.watchlistFilter.trim().toLowerCase();
    const filtered = this.watchlist.filter((instrument) =>
      !query || instrument.symbol.toLowerCase().includes(query) || instrument.name.toLowerCase().includes(query)
    );
    return [...filtered].sort((left, right) => {
      if (this.sortMode === 'change') {
        return (this.getDailyChange(right.symbol) ?? -Infinity) - (this.getDailyChange(left.symbol) ?? -Infinity);
      }
      if (this.sortMode === 'price') {
        return (this.getLatestPrice(right.symbol) ?? -Infinity) - (this.getLatestPrice(left.symbol) ?? -Infinity);
      }
      return left.symbol.localeCompare(right.symbol);
    });
  }

  get freshnessLabel(): string {
    if (!this.lastUpdated) {
      return 'Waiting for market data';
    }
    if (Object.values(this.watchlistDetails).some((detail) => detail && !detail.live)) {
      return `Last available session · updated ${this.lastUpdated.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`;
    }
    return `Updated ${this.lastUpdated.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`;
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
        this.dismissSuccessAfterDelay();
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
          this.dismissSuccessAfterDelay();
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
    return this.watchlistPrices[symbol] ?? this.watchlistDetails[symbol]?.price ?? null;
  }

  getDailyChange(symbol: string): number | null {
    return this.watchlistDetails[symbol]?.changePercent ?? null;
  }

  getSessionHigh(symbol: string): number | null {
    return this.watchlistDetails[symbol]?.high ?? null;
  }

  getSessionLow(symbol: string): number | null {
    return this.watchlistDetails[symbol]?.low ?? null;
  }

  getPriceStatus(symbol: string): string {
    const details = this.watchlistDetails[symbol];
    return details ? (details.live ? 'Live' : 'Last available session') : 'Unavailable';
  }

  sparklinePoints(symbol: string): string {
    const values = this.watchlistDetails[symbol]?.bars ?? [];
    if (values.length < 2) {
      return '';
    }
    const minimum = Math.min(...values);
    const spread = Math.max(...values) - minimum || 1;
    return values.map((value, index) => {
      const x = (index / (values.length - 1)) * 100;
      const y = 28 - ((value - minimum) / spread) * 24;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  }

  formatDailyChange(symbol: string): string {
    const change = this.getDailyChange(symbol);
    return change === null ? '—' : `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`;
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
      this.marketData.getQuote(instrument.symbol).pipe(
        map((response) => response.price),
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
    this.fetchWatchlistDetails().subscribe((details) => {
      this.watchlistDetails = details;
      this.cdr.detectChanges();
    });
    this.priceLoading = false;
    this.lastUpdated = new Date();
    if (Object.values(prices).some((price) => price === null)) {
      this.priceError = 'Some live prices are currently unavailable.';
    }
    this.cdr.detectChanges();
  }

  private fetchWatchlistDetails(): Observable<Record<string, WatchlistDetails | null>> {
    if (this.watchlist.length === 0 || typeof this.tradingApi.getInstrumentIntraday !== 'function') {
      return of({});
    }

    return forkJoin(this.watchlist.map((instrument) =>
      this.marketData.getIntraday(instrument.symbol).pipe(
        map((response) => this.toWatchlistDetails(response)),
        catchError(() => of(null))
      )
    )).pipe(
      map((details) => details.reduce<Record<string, WatchlistDetails | null>>((result, detail, index) => {
        result[this.watchlist[index].symbol] = detail;
        return result;
      }, {}))
    );
  }

  private toWatchlistDetails(response: IntradayPriceResponse): WatchlistDetails | null {
    if (response.bars.length === 0) {
      return null;
    }

    const first = response.bars[0];
    const last = response.bars[response.bars.length - 1];
    const now = new Date();
    const easternDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York' }).format(now);
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/New_York', weekday: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
    }).formatToParts(now);
    const weekday = parts.find((part) => part.type === 'weekday')?.value;
    const hour = Number(parts.find((part) => part.type === 'hour')?.value);
    const minute = Number(parts.find((part) => part.type === 'minute')?.value);
    const live = response.sessionDate === easternDate && weekday !== 'Sat' && weekday !== 'Sun'
      && (hour > 9 || (hour === 9 && minute >= 30)) && hour < 16;

    return {
      price: last.close,
      changePercent: first.open === 0 ? null : ((last.close - first.open) / first.open) * 100,
      high: Math.max(...response.bars.map((bar) => bar.high)),
      low: Math.min(...response.bars.map((bar) => bar.low)),
      live,
      bars: response.bars.map((bar) => bar.close)
    };
  }

  private clearSuccessTimeout(): void {
    if (this.successTimeout) {
      clearTimeout(this.successTimeout);
      this.successTimeout = undefined;
    }
  }

  private dismissSuccessAfterDelay(): void {
    this.clearSuccessTimeout();
    this.successTimeout = setTimeout(() => {
      this.success = '';
      this.successTimeout = undefined;
      this.cdr.detectChanges();
    }, 3000);
  }
}
