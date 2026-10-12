import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthSessionService } from '../../auth/auth-session.service';

@Component({
  imports: [CommonModule],
  selector: 'app-dashboard',
  styleUrl: './dashboard.css',
  templateUrl: './dashboard.html',
  standalone: true
})
export class DashboardComponent {
  user = this.authSession.getCurrentUser();

  constructor(
    private readonly authSession: AuthSessionService,
    private readonly router: Router
  ) {}

  logout(): void {
    this.authSession.logout();
    void this.router.navigate(['/login']);
  }
}