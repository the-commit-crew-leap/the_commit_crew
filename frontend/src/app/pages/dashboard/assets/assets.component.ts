import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthSessionService } from '../../../auth/auth-session.service';
import { PositionResponse } from '../../../api/trading.models';
import { TradingApiService } from '../../../api/trading-api.service';

@Component({
  selector: 'app-assets',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './assets.component.html',
  styleUrl: './assets.component.css'
})
export class AssetsComponent implements OnInit {
  positions: PositionResponse[] = [];
  loading = true;
  error = '';

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

    this.tradingApi.getPositions(accountId).subscribe({
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
