import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, finalize, catchError, timeout } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { CommonModule } from '@angular/common';
import { getAuthErrorMessage } from '../../auth/auth-error.util';

@Component({
  standalone: true,
  selector: 'app-register',
  templateUrl: './register.html',
  styleUrl: './register.css',
  imports: [CommonModule, ReactiveFormsModule, RouterLink]
})
export class RegisterComponent {
  isSubmitting = false;
  hasSubmitError = false;
  errorMessage = '';

  form = this.fb.nonNullable.group({
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(30),
        Validators.pattern(/^[a-zA-Z0-9_-]+$/)
      ]
    ],
    email: ['', [Validators.required, Validators.email]],
    fullName: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(255)]],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[a-zA-Z\d@$!%*?&]+$/)
      ]
    ]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly authSession: AuthSessionService,
    private readonly router: Router
  ) {}

  submit(): void {
    if (this.form.invalid || this.isSubmitting) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage = '';
    this.hasSubmitError = false;
    this.isSubmitting = true;

    this.authSession.register(this.form.getRawValue())
      .pipe(
        timeout(15000),
        finalize(() => {
          this.isSubmitting = false;
          console.log('Finalize executed. isSubmitting:', this.isSubmitting);
        }),
      )
      .subscribe({
        next: () => {
          void this.router.navigate(['/login'], {
            queryParams: { registered: '1' },
          });
        },
        error: (err) => {
          this.hasSubmitError = true;
          this.errorMessage = getAuthErrorMessage(err, 'Could not register. Please try again.');

          console.log('Error message:', this.errorMessage);
          console.log('Has submit error:', this.hasSubmitError);
          console.log('Is submitting before finalize:', this.isSubmitting);
        },
      });
  }
}