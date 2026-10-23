import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
    AccountResponse,
    BalanceResponse,
    OrderResponse,
    PositionResponse,
    PlaceOrderRequest,
    InstrumentResponse,
    PriceResponse,
    IntradayPriceResponse,
    FundamentalsResponse,
    MarketTickerResponse
} from './trading.models';

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

    getPositions(accountId: string): Observable<PositionResponse[]> {
        return this.http.get<PositionResponse[]>(`${this.baseUrl}/accounts/${accountId}/positions`);
    }

    getAccountOrders(accountId: string): Observable<OrderResponse[]> {
        return this.http.get<OrderResponse[]>(`${this.baseUrl}/accounts/${accountId}/orders`);
    }

    placeOrder(request: PlaceOrderRequest): Observable<OrderResponse> {
        return this.http.post<OrderResponse>(`${this.baseUrl}/orders`, request);
    }

    getInstruments(tradable = true): Observable<InstrumentResponse[]> {
        return this.http.get<InstrumentResponse[]>(`${this.baseUrl}/instruments`, {
            params: { tradable }
        });
    }

    getInstrumentPrice(symbol: string): Observable<PriceResponse> {
        return this.http.get<PriceResponse>(
            `${this.baseUrl}/instruments/${encodeURIComponent(symbol)}/price`
        );
    }

    getInstrumentIntraday(symbol: string): Observable<IntradayPriceResponse> {
        return this.http.get<IntradayPriceResponse>(
            `${this.baseUrl}/instruments/${encodeURIComponent(symbol)}/intraday`
        );
    }

    getInstrumentFundamentals(symbol: string): Observable<FundamentalsResponse> {
        return this.http.get<FundamentalsResponse>(
            `${this.baseUrl}/instruments/${encodeURIComponent(symbol)}/fundamentals`
        );
    }

    getMarketTicker(): Observable<MarketTickerResponse[]> {
        return this.http.get<MarketTickerResponse[]>(`${this.baseUrl}/instruments/markets`);
    }

    getWatchlist(accountId: string): Observable<InstrumentResponse[]> {
        return this.http.get<InstrumentResponse[]>(`${this.baseUrl}/accounts/${accountId}/watchlist`);
    }

    addToWatchlist(accountId: string, symbol: string): Observable<InstrumentResponse> {
        return this.http.post<InstrumentResponse>(`${this.baseUrl}/accounts/${accountId}/watchlist`, { symbol });
    }

    removeFromWatchlist(accountId: string, symbol: string): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/accounts/${accountId}/watchlist/${encodeURIComponent(symbol)}`);
    }
}