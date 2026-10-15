import { Component, ChangeDetectorRef } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { CommonModule } from '@angular/common';
import { getAuthErrorMessage } from '../../auth/auth-error.util';
import { AuthHeaderComponent } from '../../auth-header/auth-header.component';

@Component({
  standalone: true,
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AuthHeaderComponent]
})
export class LoginComponent {
  isSubmitting = false;
  errorMessage = '';
  infoMessage = '';

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
    rememberMe: [false]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly authSession: AuthSessionService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly cdr: ChangeDetectorRef
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

    this.authSession.login(
      {
        username: this.form.controls.username.value,
        password: this.form.controls.password.value
      },
      this.form.controls.rememberMe.value
    )
      .pipe(finalize(() => {
        this.isSubmitting = false;
      }))
      .subscribe({
        next: () => {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/dashboard';
          void this.router.navigateByUrl(returnUrl);
        },
        error: (err) => {
          this.errorMessage = getAuthErrorMessage(err, 'Login failed. Please try again.');
          this.cdr.detectChanges();
        }
      });
  }
}