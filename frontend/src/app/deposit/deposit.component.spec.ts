import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { DepositModalComponent } from './deposit.component';
import { DepositService } from '../services/deposit.service';

describe('DepositModalComponent', () => {
  let component: DepositModalComponent;
  let fixture: ComponentFixture<DepositModalComponent>;
  let depositService: jest.Mocked<DepositService>;

  const balance = {
    accountId: 'ACC-123',
    cashBalance: {
      amount: '1000.00',
      currency: 'USD',
    },
  };

  beforeEach(() => {
    depositService = {
      balance: signal(null),
      balanceLoading: signal(false),
      getBalance: jest.fn(),
      updateBalance: jest.fn(),
      deposit: jest.fn(),
    } as unknown as jest.Mocked<DepositService>;

    TestBed.configureTestingModule({
      imports: [DepositModalComponent],
      providers: [
        {
          provide: DepositService,
          useValue: depositService,
        },
      ],
    });

    fixture = TestBed.createComponent(DepositModalComponent);
    component = fixture.componentInstance;

    component.accountId = 'ACC-123';
  });

  it('loads the balance when the modal becomes visible', () => {
    depositService.getBalance.mockReturnValue(of(balance));

    component.ngOnChanges({
      visible: {
        currentValue: true,
        previousValue: false,
        firstChange: true,
        isFirstChange: () => true,
      },
    });

    expect(depositService.getBalance).toHaveBeenCalledWith('ACC-123');
  });

  it('rejects a deposit when no account is selected', () => {
    component.accountId = '';

    component.submitDeposit();

    expect(component.errorMessage()).toBe('No account is selected.');
    expect(depositService.deposit).not.toHaveBeenCalled();
  });

  it('rejects zero or negative deposit amounts', () => {
    component.amount.set(0);

    component.submitDeposit();

    expect(component.errorMessage()).toBe(
      'Enter a valid deposit amount greater than zero.',
    );
    expect(depositService.deposit).not.toHaveBeenCalled();
  });

  it('submits the deposit and updates the shared balance', () => {
    depositService.deposit.mockReturnValue(
      of({
        accountId: 'ACC-123',
        transactionType: 'DEPOSIT',
        amount: 500,
        newBalance: {
          amount: '1500.00',
          currency: 'USD',
        },
        timestamp: '2026-10-15T12:00:00Z',
      }),
    );
    const depositedSpy = jest.spyOn(component.deposited, 'emit');

    component.amount.set(500);
    component.submitDeposit();

    expect(depositService.deposit).toHaveBeenCalledWith('ACC-123', 500);
    expect(depositService.updateBalance).toHaveBeenCalledWith({
      accountId: 'ACC-123',
      cashBalance: {
        amount: '1500.00',
        currency: 'USD',
      },
    });
    expect(component.success()).toBe(true);
    expect(component.loading()).toBe(false);
    expect(depositedSpy).toHaveBeenCalled();
  });

  it('displays the backend validation error', () => {
    depositService.deposit.mockReturnValue(
      throwError(() => ({
        status: 400,
        error: {
          message: 'Deposit amount exceeds the limit',
        },
      })),
    );

    component.submitDeposit();

    expect(component.errorMessage()).toBe(
      'Deposit amount exceeds the limit',
    );
    expect(component.success()).toBe(false);
    expect(component.loading()).toBe(false);
  });

  it('resets the form state when closed', () => {
    component.amount.set(1000);
    component.success.set(true);
    component.errorMessage.set('Previous error');

    const closedSpy = jest.spyOn(component.closed, 'emit');

    component.close();

    expect(closedSpy).toHaveBeenCalled();
    expect(component.amount()).toBe(500);
    expect(component.success()).toBe(false);
    expect(component.errorMessage()).toBe('');
  });

  it('does not close while a deposit is loading', () => {
    component.loading.set(true);

    const closedSpy = jest.spyOn(component.closed, 'emit');

    component.close();

    expect(closedSpy).not.toHaveBeenCalled();
  });

  it('focuses the close button when the modal opens', fakeAsync(() => {
    depositService.getBalance.mockReturnValue(of(balance));
    fixture.componentRef.setInput('visible', true);

    fixture.detectChanges();
    tick();

    expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('.tcc-modal-close'),
    );
  }));

  it('closes when Escape is pressed in the dialog', () => {
    component.visible = true;
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector(
      '[role="dialog"]',
    ) as HTMLElement;
    const closedSpy = jest.spyOn(component.closed, 'emit');

    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(closedSpy).toHaveBeenCalled();
  });

  it('wraps focus from the first element to the last on Shift+Tab', () => {
    component.visible = true;
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector(
      '[role="dialog"]',
    ) as HTMLElement;
    const focusableElements = dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled])',
    );
    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];
    firstElement.focus();

    dialog.dispatchEvent(
      new KeyboardEvent('keydown', {
        key: 'Tab',
        shiftKey: true,
        bubbles: true,
      }),
    );

    expect(document.activeElement).toBe(lastElement);
  });
});