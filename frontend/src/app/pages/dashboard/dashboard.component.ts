import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthSessionService } from '../../auth/auth-session.service';
import { DepositService } from '../../services/deposit.service';
import { TradingApiService } from '../../api/trading-api.service';
import { BalanceResponse, PositionResponse } from '../../api/trading.models';
import { Order } from '../../models/order.model';
import { Subject, forkJoin, interval, startWith, switchMap, takeUntil } from 'rxjs';
import { OrderTicketComponent } from './order-ticket/order-ticket.component';
import { AssetsComponent } from './assets/assets.component';

@Component({
  imports: [CommonModule, RouterLink, OrderTicketComponent, AssetsComponent],
  selector: 'app-dashboard',
  styleUrl: './dashboard.css',
  templateUrl: './dashboard.html',
  standalone: true
})

export class DashboardComponent implements OnDestroy, OnInit {
  private readonly depositService = inject(DepositService);
  readonly balance = this.depositService.balance;
  //balance: BalanceResponse | null = null;
  balanceError: boolean = false;
  positions: PositionResponse[] = [];
  orders: Order[] = [];
  overviewError = false;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly authSession: AuthSessionService,
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

    interval(500).pipe(
      startWith(0),
      switchMap(() => forkJoin({
        balance: this.tradingApi.getBalance(accountId),
        positions: this.tradingApi.getPositions(accountId),
        orders: this.tradingApi.getAccountOrders(accountId)
      })),
      takeUntil(this.destroy$)
    ).subscribe({
      next: ({ balance, positions, orders }) => {
        //this.balance = balance;
        this.positions = positions;
        this.orders = orders;
        this.balanceError = false;
        this.overviewError = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to refresh dashboard data', error);
        this.balanceError = true;
        this.overviewError = true;
        this.cdr.detectChanges();
      }
    });
  }

    ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.complete();
    }

    get portfolioValue(): number {
      return this.positions.reduce((total, position) => total + (position.marketValue ?? 0), 0);
    }

    get todayReturn(): number {
      return this.positions.reduce((total, position) => total + (position.unrealizedPnL ?? 0), 0);
    }

    get openOrders(): number {
      return this.orders.filter((order) => order.status === 'NEW').length;
    }

    formatCurrency(value: number): string {
      return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
  
}