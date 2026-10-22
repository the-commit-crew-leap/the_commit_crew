import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PortfolioCatService {
  private readonly storageKey = 'portfolio-cat-visible';
  readonly visible = signal(this.readVisibility());

  toggle(): void {
    const visible = !this.visible();
    this.visible.set(visible);
    this.persistVisibility(visible);
  }

  private readVisibility(): boolean {
    if (typeof localStorage === 'undefined') {
      return false;
    }

    try {
      return localStorage.getItem(this.storageKey) === 'true';
    } catch (error) {
      console.warn('Unable to read the portfolio cat preference.', error);
      return false;
    }
  }

  private persistVisibility(visible: boolean): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    try {
      localStorage.setItem(this.storageKey, String(visible));
    } catch (error) {
      console.warn('Unable to save the portfolio cat preference.', error);
    }
  }
}
