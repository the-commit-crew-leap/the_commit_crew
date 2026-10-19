import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TCC_HeaderComponent } from './header/header.component';
import { NgIf } from '@angular/common';
import { AuthSessionService } from './auth/auth-session.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, TCC_HeaderComponent, NgIf],
  template: `
  <a class="skip-link" href="#main-content">Skip to main content</a>
  <app-header *ngIf="authSession.isAuthenticated()"></app-header>
  <router-outlet></router-outlet>
  `,
  styles: [`
    .skip-link {
      position: fixed;
      top: 0;
      left: 1rem;
      z-index: 2000;
      padding: 0.75rem 1rem;
      transform: translateY(-150%);
      border-radius: 0 0 6px 6px;
      background: #a7b8fa;
      color: #0d1015;
      font-weight: 600;
      text-decoration: none;
      transition: transform 120ms ease-in-out;
    }

    .skip-link:focus {
      transform: translateY(0);
    }
  `]
})
export class AppComponent {
  constructor(readonly authSession: AuthSessionService) {}
}