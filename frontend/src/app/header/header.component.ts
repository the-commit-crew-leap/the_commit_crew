import { Component, ElementRef, HostListener, inject, signal, ViewChild } from '@angular/core';
import { DepositModalComponent } from '../deposit/deposit.component';
import { AuthSessionService } from '../auth/auth-session.service';
import { NgIf } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { PortfolioCatService } from '../services/portfolio-cat.service';

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
    private readonly hostElement = inject(ElementRef<HTMLElement>);
    readonly depositModalVisible = signal(false);
    readonly accountId = this.authSession.accountId;
    @ViewChild('accountButton') private accountButton?: ElementRef<HTMLButtonElement>;
    @ViewChild('logoutButton') private logoutButton?: ElementRef<HTMLButtonElement>;
    @ViewChild('depositButton') private depositButton?: ElementRef<HTMLButtonElement>;

    constructor( 
      private readonly authSession: AuthSessionService, 
      private readonly router: Router,
      readonly portfolioCat: PortfolioCatService
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
      setTimeout(() => this.depositButton?.nativeElement.focus());
    }

    onDepositSuccess(): void {
      // DepositModalComponent updates the shared balance from the response.
    }

    togglePortfolioCat(): void {
      this.portfolioCat.toggle();
      this.accountMenuOpen = false;
    }
  accountMenuOpen = false;

  toggleAccountMenu(): void {
    this.accountMenuOpen = !this.accountMenuOpen;

    if (this.accountMenuOpen) {
      setTimeout(() => this.logoutButton?.nativeElement.focus());
    } else {
      this.accountButton?.nativeElement.focus();
    }
  }

  @HostListener('document:keydown.escape')
  closeAccountMenuOnEscape(): void {
    if (!this.accountMenuOpen) {
      return;
    }

    this.accountMenuOpen = false;
    this.accountButton?.nativeElement.focus();
  }

  @HostListener('document:click', ['$event'])
  closeAccountMenuOnOutsideClick(event: MouseEvent): void {
    if (
      this.accountMenuOpen &&
      !this.hostElement.nativeElement.contains(event.target as Node)
    ) {
      this.accountMenuOpen = false;
    }
  }

  logout(): void {
    this.authSession.logout();
    this.accountMenuOpen = false;
    void this.router.navigate(['/login']);
  }

}
