import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  AfterViewChecked,
  Output,
  ViewChild,
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
export class DepositModalComponent implements OnChanges, AfterViewChecked {
  private readonly depositService = inject(DepositService);
  private shouldFocusDialog = false;

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
  @ViewChild('dialog') private dialog?: ElementRef<HTMLElement>;
  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue === true) {
      this.shouldFocusDialog = true;
    }

    if (changes['visible']?.currentValue === true && this.accountId) {
      this.depositService.getBalance(this.accountId).subscribe({
        error: () => {
          this.errorMessage.set('Unable to load your current balance.');
        },
      });
    }
  }

  ngAfterViewChecked(): void {
    if (!this.shouldFocusDialog || !this.visible || !this.closeButton) {
      return;
    }

    this.shouldFocusDialog = false;
    this.closeButton.nativeElement.focus();
  }

  onDialogKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
      return;
    }

    if (event.key !== 'Tab') {
      return;
    }

    const focusableElements = this.getFocusableElements();
    if (focusableElements.length === 0) {
      event.preventDefault();
      this.dialog?.nativeElement.focus();
      return;
    }

    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];
    const activeElement = document.activeElement;

    if (event.shiftKey && activeElement === firstElement) {
      event.preventDefault();
      lastElement.focus();
    } else if (!event.shiftKey && activeElement === lastElement) {
      event.preventDefault();
      firstElement.focus();
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
        next: (response) => {
          // The deposit has been confirmed by the backend.
          this.success.set(true);
          this.depositService.updateBalance({
            accountId: response.accountId,
            cashBalance: response.newBalance,
          });
          this.deposited.emit();
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

  private getFocusableElements(): HTMLElement[] {
    const dialog = this.dialog?.nativeElement;
    if (!dialog) {
      return [];
    }

    return Array.from(
      dialog.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), [href], select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ),
    );
  }
}