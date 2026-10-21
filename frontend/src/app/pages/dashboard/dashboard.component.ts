import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { AuthSessionService } from '../../auth/auth-session.service';
import { DepositService } from '../../services/deposit.service';
import { TradingApiService } from '../../api/trading-api.service';
import { BalanceResponse, PositionResponse } from '../../api/trading.models';
import { Order } from '../../models/order.model';
import { EMPTY, Subject, catchError, exhaustMap, forkJoin, interval, startWith, takeUntil } from 'rxjs';
import { OrderTicketComponent } from './order-ticket/order-ticket.component';
import { AssetsComponent } from './assets/assets.component';
import { WatchlistComponent } from './watchlist/watchlist.component';
import { InstrumentStatsModalComponent } from './instrument-stats-modal/instrument-stats-modal.component';
import { isUsMarketOpen } from '../../shared/market-status';

@Component({
  imports: [CommonModule, OrderTicketComponent, AssetsComponent, WatchlistComponent, InstrumentStatsModalComponent],
  selector: 'app-dashboard',
  styleUrl: './dashboard.css',
  templateUrl: './dashboard.html',
  standalone: true
})

export class DashboardComponent implements OnDestroy, OnInit {
  private readonly depositService = inject(DepositService);
  readonly balance = this.depositService.balance;
  balanceError: boolean = false;
  positions: PositionResponse[] = [];
  orders: Order[] = [];
  overviewError = false;
  selectedInstrument = '';
  lastUpdated: Date | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    readonly authSession: AuthSessionService,
    private readonly tradingApi: TradingApiService,
    private readonly cdr: ChangeDetectorRef
  ) {
  }

  ngOnInit(): void {
    const accountId = this.authSession.getAccountId();

    if (accountId) {
      this.depositService.getBalance(accountId).subscribe({
        error: () => {
          this.balanceError = true;
        },
      });
    }
    if (!accountId) {
      this.balanceError = true;
      return;
    }

    interval(5000).pipe(
      startWith(0),
      exhaustMap(() => forkJoin({
        balance: this.depositService.getBalance(accountId, false),
        positions: this.tradingApi.getPositions(accountId),
        orders: this.tradingApi.getAccountOrders(accountId)
      }).pipe(
        catchError((error) => {
          console.error('Failed to refresh dashboard data', error);
          this.balanceError = true;
          this.overviewError = true;
          this.cdr.detectChanges();
          return EMPTY;
        })
      )),
      takeUntil(this.destroy$)
    ).subscribe({
      next: ({ positions, orders }) => {
        this.positions = this.mergePositions(positions);
        this.orders = orders;
        this.balanceError = false;
        this.overviewError = false;
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

    get portfolioValue(): number {
      return this.positions.reduce((total, position) => total + (position.marketValue ?? 0), 0);
    }

    get portfolioValueAvailable(): boolean {
      return this.positions.length > 0 && this.positions.every((position) => position.marketValue !== null);
    }

    get hasLastSessionPrices(): boolean {
      return this.positions.some((position) => !position.live && position.currentPrice !== null);
    }

    get todayReturn(): number {
      return this.positions.reduce((total, position) => total + (position.unrealizedPnL ?? 0), 0);
    }

    get displayedUnrealizedPnL(): number {
      return this.todayReturn;
    }

    get openOrders(): number {
      return this.orders.filter((order) => order.status === 'NEW').length;
    }

    get lastTradeAt(): Date | null {
      const timestamps = this.positions
        .map((position) => position.priceUpdatedAt)
        .filter((timestamp): timestamp is string => timestamp != null)
        .map((timestamp) => new Date(timestamp).getTime())
        .filter((timestamp) => Number.isFinite(timestamp));
      const latest = Math.max(...timestamps);
      return Number.isFinite(latest) ? new Date(latest) : null;
    }

    get marketOpen(): boolean {
      return isUsMarketOpen();
    }

    get selectedInstrumentPosition(): PositionResponse | undefined {
      return this.positions.find((position) => position.symbol === this.selectedInstrument);
    }

    openInstrumentStats(symbol: string): void {
      this.selectedInstrument = symbol;
    }

    closeInstrumentStats(): void {
      this.selectedInstrument = '';
    }

    formatCurrency(value: number): string {
      return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
  
}