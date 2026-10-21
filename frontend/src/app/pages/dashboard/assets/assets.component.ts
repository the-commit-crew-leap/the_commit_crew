import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { AuthSessionService } from '../../../auth/auth-session.service';
import { PositionResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { EMPTY, Subject, catchError, exhaustMap, interval, startWith, takeUntil } from 'rxjs';
import { isUsMarketOpen } from '../../../shared/market-status';

@Component({
  selector: 'app-assets',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './assets.component.html',
  styleUrl: './assets.component.css'
})
export class AssetsComponent implements OnDestroy, OnInit {
  @Output() readonly instrumentSelected = new EventEmitter<string>();
  positions: PositionResponse[] = [];
  loading = true;
  error = '';
  lastUpdated: Date | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly authSession: AuthSessionService,
    private readonly tradingApi: TradingApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const accountId = this.authSession.getAccountId();

    if (!accountId) {
      this.error = 'No account found in session. Please log in again.';
      this.loading = false;
      return;
    }

    interval(5000).pipe(
      startWith(0),
      exhaustMap(() => this.tradingApi.getPositions(accountId).pipe(
        catchError((error) => {
          console.error('Failed to refresh account positions', error);
          this.error = 'Live prices are temporarily unavailable.';
          this.loading = false;
          this.cdr.detectChanges();
          return EMPTY;
        })
      )),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (positions) => {
        this.positions = this.mergePositions(positions);
        this.loading = false;
        this.error = '';
        this.lastUpdated = new Date();
        this.cdr.detectChanges();
      }
    });
  }

  private mergePositions(nextPositions: PositionResponse[]): PositionResponse[] {
    const previousBySymbol = new Map(this.positions.map((position) => [position.symbol, position]));
    return nextPositions.map((position) => {
      const previous = previousBySymbol.get(position.symbol);
      if (!previous) {
        return position;
      }

      return {
        ...position,
        currentPrice: position.currentPrice ?? previous.currentPrice,
        marketValue: position.marketValue ?? previous.marketValue,
        unrealizedPnL: position.unrealizedPnL ?? previous.unrealizedPnL,
        unrealizedPnLPercent: position.unrealizedPnLPercent ?? previous.unrealizedPnLPercent
      };
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  formatCurrency(value: number | null): string {
    if (value === null) {
      return '—';
    }

    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }

  formatQuantity(value: number): string {
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 4
    });
  }

  get lastTradeAt(): Date | null {
    const timestamps = this.positions
      .map((position) => position.lastTradeAt)
      .filter((timestamp): timestamp is string => timestamp !== null)
      .map((timestamp) => new Date(timestamp).getTime())
      .filter((timestamp) => Number.isFinite(timestamp));
    const latest = Math.max(...timestamps);
    return Number.isFinite(latest) ? new Date(latest) : null;
  }

  get marketOpen(): boolean {
    return isUsMarketOpen();
  }

  selectInstrument(symbol: string): void {
    this.instrumentSelected.emit(symbol);
  }
}
