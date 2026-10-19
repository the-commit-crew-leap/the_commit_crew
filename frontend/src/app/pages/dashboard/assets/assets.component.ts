import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthSessionService } from '../../../auth/auth-session.service';
import { PositionResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { EMPTY, Subject, catchError, exhaustMap, interval, startWith, takeUntil } from 'rxjs';

@Component({
  selector: 'app-assets',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './assets.component.html',
  styleUrl: './assets.component.css'
})
export class AssetsComponent implements OnDestroy, OnInit {
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
        this.positions = positions;
        this.loading = false;
        this.error = '';
        this.lastUpdated = new Date();
        this.cdr.detectChanges();
      }
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
}
