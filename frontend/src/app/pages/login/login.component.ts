import { Component, ChangeDetectorRef, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { CommonModule } from '@angular/common';
import { getAuthErrorMessage } from '../../auth/auth-error.util';
import { AuthHeaderComponent } from '../../auth-header/auth-header.component';
import { environment } from '../../../environments/environment';

@Component({
  standalone: true,
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AuthHeaderComponent]
})
export class LoginComponent {
  @ViewChild('loginForm') private loginForm?: ElementRef<HTMLFormElement>;
  @ViewChild('apiError') private apiError?: ElementRef<HTMLParagraphElement>;
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
    const oauthRememberMe = sessionStorage.getItem('oauth_remember_me') === 'true';
    if (window.location.hash && this.authSession.completeGoogleLogin(window.location.hash, oauthRememberMe)) {
      sessionStorage.removeItem('oauth_remember_me');
      window.history.replaceState({}, document.title, window.location.pathname);
      void this.router.navigateByUrl('/dashboard');
      return;
    }

    if (new URLSearchParams(window.location.search).get('oauthError')) {
      this.errorMessage = 'Google sign-in failed. Please try again.';
    }

    const query = this.route.snapshot.queryParamMap;
    if (query.get('registered') === '1') {
      this.infoMessage = 'Account created successfully. Please sign in.';
    } else if (query.get('expired') === '1') {
      this.infoMessage = 'Session expired. Please sign in again.';
    }

  }

  continueWithGoogle(): void {
    sessionStorage.setItem('oauth_remember_me', String(this.form.controls.rememberMe.value));
    window.location.assign(`${environment.authApiBaseUrl}/google`);
  }

  submit(): void {
    if (this.form.invalid || this.isSubmitting) {
      this.form.markAllAsTouched();
      this.focusFirstInvalidField();
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
      .pipe(
        finalize(() => {
          this.isSubmitting = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/dashboard';
          void this.router.navigateByUrl(returnUrl);
        },
        error: (err) => {
          this.errorMessage = getAuthErrorMessage(err, 'Login failed. Please try again.');
          this.cdr.detectChanges();
          this.focusSubmitError();
        }
      });
  }

  private focusFirstInvalidField(): void {
    setTimeout(() => {
      this.loginForm?.nativeElement
        .querySelector<HTMLElement>('[aria-invalid="true"]')
        ?.focus();
    });
  }

  private focusSubmitError(): void {
    setTimeout(() => {
      const errorElement = this.apiError?.nativeElement;
      if (errorElement) {
        errorElement.focus();
      }
    });
  }
}