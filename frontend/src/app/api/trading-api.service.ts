import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AccountResponse, BalanceResponse } from './trading.models';

@Injectable({ providedIn: 'root' })
export class TradingApiService {
    private readonly baseUrl = environment.appApiBaseUrl;

    constructor(private readonly http: HttpClient) {}

    getAccount(accountId: string): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`${this.baseUrl}/accounts/${accountId}`);
    }

    getBalance(accountId: string): Observable<BalanceResponse> {
    return this.http.get<BalanceResponse>(`${this.baseUrl}/accounts/${accountId}/balance`);
    }
}