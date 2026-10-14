import { CommonModule } from '@angular/common';
import { Component, ChangeDetectorRef } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthSessionService } from '../../auth/auth-session.service';
import { TradingApiService } from '../../api/trading-api.service';
import { BalanceResponse } from '../../api/trading.models';

@Component({
  imports: [CommonModule, RouterLink],
  selector: 'app-dashboard',
  styleUrl: './dashboard.css',
  templateUrl: './dashboard.html',
  standalone: true
})
export class DashboardComponent {
  balance: BalanceResponse | null = null;
  balanceError: boolean = false;

  constructor(
    private readonly authSession: AuthSessionService,
    private readonly router: Router,
    private readonly tradingApi: TradingApiService,
    private readonly cdr: ChangeDetectorRef
  ) {
    const accountId = this.authSession.getAccountId();

    if (accountId) {
      this.tradingApi.getBalance(accountId).subscribe({
        next: (balance) => {
          console.log('Balance response:', balance);
          this.balance = balance;
          this.cdr.detectChanges();
        },
        error: (error) => {
          console.error('Failed to load account balance', error);
          this.balanceError = true;
          this.cdr.detectChanges();
        }
      });
    }
  }

  logout(): void {
    this.authSession.logout();
    void this.router.navigate(['/login']);
  }
}