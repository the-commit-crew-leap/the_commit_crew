import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthSessionService } from '../../auth/auth-session.service';
import { DepositService } from '../../services/deposit.service';
import { OrderTicketComponent } from './order-ticket/order-ticket.component';

@Component({
  imports: [CommonModule, RouterLink, OrderTicketComponent],
  selector: 'app-dashboard',
  styleUrl: './dashboard.css',
  templateUrl: './dashboard.html',
  standalone: true
})
export class DashboardComponent {
  private readonly depositService = inject(DepositService);
  readonly balance = this.depositService.balance;
  balanceError = false;

  constructor(
    private readonly authSession: AuthSessionService,
    private readonly router: Router,
  ) {
    const accountId = this.authSession.getAccountId();

    if (accountId) {
      this.depositService.getBalance(accountId).subscribe({
        error: () => {
          this.balanceError = true;
        },
      });
    }
  }

  logout(): void {
    this.authSession.logout();
    void this.router.navigate(['/login']);
  }
}