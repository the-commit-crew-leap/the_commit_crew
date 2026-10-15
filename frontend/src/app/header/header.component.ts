import { Component, signal } from '@angular/core';
import { DepositModalComponent } from '../deposit/deposit.component';
import { AuthSessionService } from '../auth/auth-session.service';
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
    readonly accountId = this.authSession.accountId;

    constructor( 
      private readonly authSession: AuthSessionService, 
      private readonly router: Router
    ) {
    }

    openDeposit(): void {
      const accountId = this.accountId();

      if (!accountId) {
        console.error('No authenticated account is available.');
        return;
      }

      this.depositModalVisible.set(true);
    }

    closeDeposit(): void {
      this.depositModalVisible.set(false);
    }

    onDepositSuccess(): void {
      // DepositModalComponent updates the shared balance from the response.
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
