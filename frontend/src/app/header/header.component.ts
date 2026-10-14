import { Component, signal } from '@angular/core';
import { DepositModalComponent } from '../deposit/deposit.component';
import { AuthSessionService } from '../auth/auth-session.service';
import { DepositService } from '../services/deposit.service';
import { NgIf } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-header',
  styleUrl: './header.css',
  templateUrl: './header.html',
  imports: [
    NgIf,
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
      private readonly router: Router
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
  accountMenuOpen = false;

  toggleAccountMenu(): void {
    this.accountMenuOpen = !this.accountMenuOpen;
  }

  logout(): void {
    this.authSession.logout();
    this.accountMenuOpen = false;
    void this.router.navigate(['/login']);
  }

}

