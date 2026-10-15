import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

import {
  BalanceResponse,
  DepositRequest,
  DepositResponse,
} from '../api/trading.models';

@Injectable({
  providedIn: 'root',
})
export class DepositService {
  private readonly http = inject(HttpClient);

  private readonly apiUrl = `${environment.appApiBaseUrl}/accounts`;

  readonly balance = signal<BalanceResponse | null>(null);
  readonly balanceLoading = signal(false);

  getBalance(accountId: string): Observable<BalanceResponse> {
    this.balanceLoading.set(true);

    return this.http
      .get<BalanceResponse>(
        `${this.apiUrl}/${encodeURIComponent(accountId)}/balance`,
      )
      .pipe(
        tap({
          next: response => {
            this.balance.set(response);
            this.balanceLoading.set(false);
          },
          error: () => {
            this.balanceLoading.set(false);
          },
        }),
      );
  }

  deposit(
    accountId: string,
    amount: number,
  ): Observable<DepositResponse> {
    const request: DepositRequest = { amount };

  return this.http.post<DepositResponse>(
    `${this.apiUrl}/${encodeURIComponent(accountId)}/deposit`,
    request,
  );
  }
}