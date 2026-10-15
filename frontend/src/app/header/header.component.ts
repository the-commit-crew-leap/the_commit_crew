import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { DepositModalComponent } from '../deposit/deposit.component';
import { AuthSessionService } from '../auth/auth-session.service';
import { DepositService } from '../services/deposit.service';

@Component({
  selector: 'app-header',
  styleUrl: './header.css',
  templateUrl: './header.html',
  imports: [
    RouterLink,
    RouterLinkActive,
    DepositModalComponent
  ]
})
export class TCC_HeaderComponent {
    readonly depositModalVisible = signal(false);
    accountId = '';

    constructor( 
      private readonly authSession: AuthSessionService, 
      private readonly depositService: DepositService,
    ) {
      this.accountId = this.authSession.getAccountId() ?? '';
    }

    openDeposit(): void {
      this.accountId = this.authSession.getAccountId() ?? '';

      if (!this.accountId) {
        console.error('No authenticated account is available.');
        return;
      }

      this.depositModalVisible.set(true);
    }

    closeDeposit(): void {
      this.depositModalVisible.set(false);
    }

    onDepositSuccess(): void {
      if (this.accountId) {
        this.depositService.getBalance(this.accountId).subscribe();
      }
    }
}