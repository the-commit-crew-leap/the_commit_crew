import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subject, catchError, exhaustMap, interval, of, startWith, takeUntil } from 'rxjs';

import { MarketTickerResponse } from '../api/trading.models';
import { TradingApiService } from '../api/trading-api.service';

@Component({
  selector: 'app-market-ticker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './market-ticker.component.html',
  styleUrl: './market-ticker.component.css'
})
export class MarketTickerComponent implements OnInit, OnDestroy {
  readonly markets = ['BTC/USD', 'ETH/USD', 'SOL/USD', 'AVAX/USD'];
  marketData: MarketTickerResponse[] = this.markets.map((symbol) => this.unavailable(symbol));
  loading = true;
  error = '';
  lastUpdated: Date | null = null;

  private readonly tradingApi = inject(TradingApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  ngOnInit(): void {
    interval(5000).pipe(
      startWith(0),
      exhaustMap(() => this.tradingApi.getMarketTicker().pipe(
        catchError((error) => {
          console.error('Failed to refresh market ticker', error);
          this.error = 'Market data is temporarily unavailable.';
          this.loading = false;
          this.cdr.detectChanges();
          return of(null);
        })
      )),
      takeUntil(this.destroy$)
    ).subscribe((markets) => {
      if (markets === null) {
        return;
      }

      this.marketData = this.markets.map((symbol) =>
        markets.find((market) => market.symbol === symbol) ?? this.unavailable(symbol)
      );
      this.loading = false;
      this.error = this.marketData.some((market) => !market.live)
        ? 'Some market prices are currently unavailable.'
        : '';
      this.lastUpdated = new Date();
      this.cdr.detectChanges();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackBySymbol(_index: number, market: MarketTickerResponse): string {
    return market.symbol;
  }

  changeClass(market: MarketTickerResponse): string {
    if (market.changePercent === null) {
      return 'is-neutral';
    }
    return market.changePercent >= 0 ? 'is-positive' : 'is-negative';
  }

  absoluteChange(changePercent: number): number {
    return Math.abs(changePercent);
  }

  private unavailable(symbol: string): MarketTickerResponse {
    return {
      symbol,
      price: null,
      changePercent: null,
      source: null,
      live: false,
      updatedAt: null
    };
  }
}
