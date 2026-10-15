import {
  Component,
  EventEmitter,
  Input,
  Output,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { OnChanges, SimpleChanges } from '@angular/core';
import { DepositService } from '../services/deposit.service';

@Component({
  selector: 'app-deposit-modal',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './deposit.html',
  styleUrl: './deposit.css',
})
export class DepositModalComponent implements OnChanges {
  private readonly depositService = inject(DepositService);

  @Input() visible = false;

  // Supply this from your existing authenticated account state.
  @Input({ required: true }) accountId!: string;

  @Output() closed = new EventEmitter<void>();
  @Output() deposited = new EventEmitter<void>();

  readonly amount = signal<number | null>(500);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly success = signal(false);
  readonly balanceRefreshFailed = signal(false);
  readonly balance = this.depositService.balance;
  readonly balanceLoading = this.depositService.balanceLoading;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue === true && this.accountId) {
      this.depositService.getBalance(this.accountId).subscribe({
        error: () => {
          this.errorMessage.set('Unable to load your current balance.');
        },
      });
    }
  }

  close(): void {
    if (this.loading()) {
      return;
    }

    this.closed.emit();
    this.reset();
  }

  selectAmount(value: number): void {
    this.amount.set(value);
    this.errorMessage.set('');
  }

  submitDeposit(): void {
    this.errorMessage.set('');

    const amount = this.amount();

    if (!this.accountId) {
      this.errorMessage.set('No account is selected.');
      return;
    }

    if (
      amount === null ||
      !Number.isFinite(amount) ||
      amount <= 0 ||
      this.loading()
    ) {
      this.errorMessage.set('Enter a valid deposit amount greater than zero.');
      return;
    }

    this.loading.set(true);

    this.depositService
      .deposit(this.accountId, amount)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          // The deposit has been confirmed by the backend.
          this.success.set(true);
          this.deposited.emit();

          // Fetch the canonical balance after the deposit.
          this.depositService.getBalance(this.accountId).subscribe({
            error: () => {
              // Do not treat a successful deposit as a failed deposit
              // just because refreshing the balance failed.
              this.balanceRefreshFailed.set(true);
            },
          });
        },

        error: (error: HttpErrorResponse) => {
          const apiMessage = error.error?.message;

          if (error.status === 400) {
            this.errorMessage.set(
              apiMessage ?? 'The deposit amount was rejected.',
            );
          } else if (error.status === 401) {
            this.errorMessage.set(
              'Your session has expired. Please sign in again.',
            );
          } else if (error.status === 403) {
            this.errorMessage.set(
              'You are not allowed to deposit into this account.',
            );
          } else if (error.status === 404) {
            this.errorMessage.set('The account could not be found.');
          } else if (error.status === 0) {
            this.errorMessage.set(
              'Unable to reach the server. Check your connection.',
            );
          } else {
            this.errorMessage.set(
              apiMessage ?? 'The deposit failed. Please try again.',
            );
          }
        },
      });
  }

  private reset(): void {
    this.amount.set(500);
    this.errorMessage.set('');
    this.success.set(false);
    this.balanceRefreshFailed.set(false);
  }
}