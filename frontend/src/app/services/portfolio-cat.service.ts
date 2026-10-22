import { effect, Injectable, signal } from '@angular/core';
import { AuthSessionService } from '../auth/auth-session.service';

@Injectable({ providedIn: 'root' })
export class PortfolioCatService {
  private readonly storageKeyPrefix = 'portfolio-cat-visible';
  readonly visible = signal(false);

  constructor(private readonly authSession: AuthSessionService) {
    effect(() => {
      this.visible.set(this.readVisibility(this.authSession.accountId()));
    });
  }

  toggle(): void {
    const visible = !this.visible();
    this.visible.set(visible);
    this.persistVisibility(this.authSession.accountId(), visible);
  }

  private readVisibility(accountId: string | null): boolean {
    if (!accountId || typeof localStorage === 'undefined') {
      return false;
    }

    try {
      return localStorage.getItem(this.storageKey(accountId)) === 'true';
    } catch (error) {
      console.warn('Unable to read the portfolio cat preference.', error);
      return false;
    }
  }

  private persistVisibility(accountId: string | null, visible: boolean): void {
    if (!accountId || typeof localStorage === 'undefined') {
      return;
    }

    try {
      localStorage.setItem(this.storageKey(accountId), String(visible));
    } catch (error) {
      console.warn('Unable to save the portfolio cat preference.', error);
    }
  }

  private storageKey(accountId: string): string {
    return `${this.storageKeyPrefix}:${accountId}`;
  }
}
