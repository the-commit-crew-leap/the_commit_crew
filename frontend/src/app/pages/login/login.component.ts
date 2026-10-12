import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { CommonModule } from '@angular/common';

@Component({
  standalone: true,
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
  imports: [CommonModule, ReactiveFormsModule, RouterLink]
})
export class LoginComponent {
  isSubmitting = false;
  errorMessage = '';
  infoMessage = '';

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly authSession: AuthSessionService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {
    const query = this.route.snapshot.queryParamMap;
    if (query.get('registered') === '1') {
      this.infoMessage = 'Account created successfully. Please sign in.';
    } else if (query.get('expired') === '1') {
      this.infoMessage = 'Session expired. Please sign in again.';
    }
  }

  submit(): void {
    if (this.form.invalid || this.isSubmitting) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage = '';
    this.isSubmitting = true;

    this.authSession.login(this.form.getRawValue())
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: () => {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/';
          void this.router.navigateByUrl(returnUrl);
        },
        error: (err) => {
          this.errorMessage = err?.error?.message || 'Login failed. Please try again.';
        }
      });
  }
}