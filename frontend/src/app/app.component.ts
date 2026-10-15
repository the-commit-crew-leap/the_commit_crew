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
  <app-header *ngIf="authSession.isAuthenticated()"></app-header>
  <router-outlet></router-outlet>
  `,
  styles: []
})
export class AppComponent {
  constructor(readonly authSession: AuthSessionService) {}
}