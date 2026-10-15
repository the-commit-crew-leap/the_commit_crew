import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TimeoutError, catchError, forkJoin, map, of, timeout } from 'rxjs';

import { AuthSessionService } from '../../../auth/auth-session.service';
import { InstrumentResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';

@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './watchlist.component.html',
  styleUrl: './watchlist.component.css'
})
export class WatchlistComponent implements OnInit {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradingApi = inject(TradingApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  instruments: InstrumentResponse[] = [];
  watchlist: InstrumentResponse[] = [];
  watchlistPrices: Record<string, number | null> = {};
  selectedSymbol = '';
  loading = true;
  priceLoading = false;
  saving = false;
  removingSymbol = '';
  error = '';
  success = '';

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
        this.loadWatchlistPrices(watchlist);
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

  get availableInstruments(): InstrumentResponse[] {
    const symbols = new Set(this.watchlist.map((instrument) => instrument.symbol));
    return this.instruments.filter((instrument) => !symbols.has(instrument.symbol));
  }

  addInstrument(): void {
    const accountId = this.authSession.getAccountId();
    if (!accountId || !this.selectedSymbol || this.saving) {
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
        this.loadWatchlistPrices(this.watchlist);
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

  private reconcileTimedOutAdd(accountId: string, symbol: string): void {
    this.tradingApi.getWatchlist(accountId).subscribe({
      next: (watchlist) => {
        const instrument = watchlist.find((item) => item.symbol === symbol);
        if (instrument) {
          this.watchlist = watchlist;
          this.loadWatchlistPrices(watchlist);
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

  private loadWatchlistPrices(watchlist: InstrumentResponse[]): void {
    this.watchlistPrices = {};
    if (watchlist.length === 0) {
      this.priceLoading = false;
      return;
    }

    this.priceLoading = true;
    forkJoin(watchlist.map((instrument) =>
      this.tradingApi.getInstrumentPrice(instrument.symbol).pipe(
        map((response) => response.price),
        catchError((error) => {
          console.error(`Failed to load latest price for ${instrument.symbol}`, error);
          return of(null);
        })
      )
    )).subscribe((prices) => {
      this.watchlistPrices = prices.reduce<Record<string, number | null>>((result, price, index) => {
        result[watchlist[index].symbol] = price;
        return result;
      }, {});
      this.priceLoading = false;
      this.cdr.detectChanges();
    });
  }
}
