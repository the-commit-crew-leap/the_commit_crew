import { Injectable } from '@angular/core';
import { Observable, catchError, finalize, shareReplay, throwError } from 'rxjs';

import { IntradayPriceResponse, PriceResponse } from '../api/trading.models';
import { TradingApiService } from '../api/trading-api.service';

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly cache = new Map<string, Observable<PriceResponse>>();
  private readonly intradayCache = new Map<string, Observable<IntradayPriceResponse>>();

  constructor(private readonly tradingApi: TradingApiService) {}

  getQuote(symbol: string): Observable<PriceResponse> {
    const normalizedSymbol = symbol.trim().toUpperCase();
    const cached = this.cache.get(normalizedSymbol);
    if (cached) {
      return cached;
    }

    const request = this.tradingApi.getInstrumentPrice(normalizedSymbol).pipe(
      catchError((error) => {
        this.cache.delete(normalizedSymbol);
        return throwError(() => error);
      }),
      finalize(() => this.cache.delete(normalizedSymbol)),
      shareReplay({ bufferSize: 1, refCount: true, windowTime: 4000 })
    );
    this.cache.set(normalizedSymbol, request);
    return request;
  }

  getIntraday(symbol: string): Observable<IntradayPriceResponse> {
    const normalizedSymbol = symbol.trim().toUpperCase();
    const cached = this.intradayCache.get(normalizedSymbol);
    if (cached) {
      return cached;
    }

    const request = this.tradingApi.getInstrumentIntraday(normalizedSymbol).pipe(
      catchError((error) => {
        this.intradayCache.delete(normalizedSymbol);
        return throwError(() => error);
      }),
      finalize(() => this.intradayCache.delete(normalizedSymbol)),
      shareReplay({ bufferSize: 1, refCount: true, windowTime: 4000 })
    );
    this.intradayCache.set(normalizedSymbol, request);
    return request;
  }

  clear(symbol?: string): void {
    if (symbol) {
      const normalizedSymbol = symbol.trim().toUpperCase();
      this.cache.delete(normalizedSymbol);
      this.intradayCache.delete(normalizedSymbol);
      return;
    }
    this.cache.clear();
    this.intradayCache.clear();
  }
}
