import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { AuthSessionService } from '../../../auth/auth-session.service';
import { PositionResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';
import { Subject, exhaustMap, interval, startWith, takeUntil } from 'rxjs';

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

    interval(1000).pipe(
      startWith(0),
      exhaustMap(() => this.tradingApi.getPositions(accountId)),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (positions) => {
        this.positions = positions;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Failed to load account positions', error);
        this.error = 'Unable to load your assets.';
        this.loading = false;
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

  selectInstrument(symbol: string): void {
    this.instrumentSelected.emit(symbol);
  }
}
