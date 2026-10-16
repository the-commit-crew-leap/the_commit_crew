import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PlotlyFigure {
  data: unknown[];
  layout: Record<string, unknown>;
  config?: Record<string, unknown>;
}

export interface AnalyticsStatistic {
  Ticker: string;
  'Asset Class': string;
  Observations: number;
  'Annualized Return (%)': number;
  'Annualized Volatility (%)': number;
  'Sharpe Ratio': number | null;
  'Best Daily Return (%)'?: number;
  'Worst Daily Return (%)'?: number;
}

export interface AnalyticsInsights {
  metadata: {
    generated_at: string;
    period: string;
    tickers: string[];
    ticker_count: number;
  };
  statistics: AnalyticsStatistic[];
  insights: {
    best_return: AnalyticsMetric | null;
    worst_return: AnalyticsMetric | null;
    highest_volatility: AnalyticsMetric | null;
    lowest_volatility: AnalyticsMetric | null;
    best_sharpe: AnalyticsMetric | null;
    risk_ranking: Array<{ rank: number; ticker: string; volatility: number }>;
  };
}

export interface AnalyticsMetric {
  ticker: string;
  value: number;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly baseUrl = environment.analyticsApiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  getInsights(symbols: string[], period: string, volatilityWindow: number): Observable<AnalyticsInsights> {
    return this.http.get<AnalyticsInsights>(`${this.baseUrl}/insights`, {
      params: this.buildParams(symbols, period, volatilityWindow)
    });
  }

  getCorrelation(symbols: string[], period: string): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/charts/correlation`, {
      params: this.buildParams(symbols, period)
    });
  }

  getVolatility(symbols: string[], period: string, volatilityWindow: number): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/charts/volatility`, {
      params: this.buildParams(symbols, period, volatilityWindow)
    });
  }

  getAssetClassVolatility(symbols: string[], period: string): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/charts/asset-class-volatility`, {
      params: this.buildParams(symbols, period)
    });
  }

  private buildParams(symbols: string[], period: string, volatilityWindow?: number): HttpParams {
    let params = new HttpParams().set('period', period);
    symbols.forEach((symbol) => {
      params = params.append('tickers', symbol);
    });

    if (volatilityWindow !== undefined) {
      params = params.set('volatility_window', volatilityWindow);
    }

    return params;
  }
}
