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
  'Maximum Drawdown (%)'?: number;
  'Current Drawdown (%)'?: number;
  'Max Drawdown Duration (days)'?: number;
  'Historical VaR 95% (%)'?: number;
  'Expected Shortfall 95% (%)'?: number;
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

export interface PortfolioInsights {
  period: string;
  symbols: string[];
  weights: number[];
  statistics: AnalyticsStatistic[];
}

export interface PortfolioScenarioSnapshot {
  total_value: number;
  symbols: string[];
  weights: number[];
  statistics: AnalyticsStatistic[];
}

export interface PortfolioScenario {
  period: string;
  scenario: {
    symbol: string;
    additional_quantity: number;
    purchase_price: number;
    purchase_value: number;
  };
  current: PortfolioScenarioSnapshot;
  proposed: PortfolioScenarioSnapshot;
}

export interface PortfolioTransaction {
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  createdOn: string;
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

  getVolatility(symbols: string[], period: string, volatilityWindow: number): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/charts/volatility`, {
      params: this.buildParams(symbols, period, volatilityWindow)
    });
  }

  getDrawdown(symbols: string[], period: string): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/charts/drawdown`, {
      params: this.buildParams(symbols, period)
    });
  }

  getCandlestick(symbol: string, period: string): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/charts/candlestick`, {
      params: new HttpParams().set('ticker', symbol).set('period', period)
    });
  }

  getPortfolioInsights(symbols: string[], weights: number[], period: string, volatilityWindow: number, transactions: PortfolioTransaction[] = []): Observable<PortfolioInsights> {
    return this.http.get<PortfolioInsights>(`${this.baseUrl}/portfolio/insights`, {
      params: this.buildPortfolioParams(symbols, weights, period, volatilityWindow, transactions)
    });
  }

  getPortfolioCumulative(symbols: string[], weights: number[], period: string, transactions: PortfolioTransaction[] = []): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/portfolio/charts/cumulative`, {
      params: this.buildPortfolioParams(symbols, weights, period, undefined, transactions)
    });
  }

  getPortfolioVolatility(symbols: string[], weights: number[], period: string, volatilityWindow: number, transactions: PortfolioTransaction[] = []): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/portfolio/charts/volatility`, {
      params: this.buildPortfolioParams(symbols, weights, period, volatilityWindow, transactions)
    });
  }

  getPortfolioDrawdown(symbols: string[], weights: number[], period: string, transactions: PortfolioTransaction[] = []): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/portfolio/charts/drawdown`, {
      params: this.buildPortfolioParams(symbols, weights, period, undefined, transactions)
    });
  }

  getPortfolioAllocation(symbols: string[], weights: number[]): Observable<PlotlyFigure> {
    return this.http.get<PlotlyFigure>(`${this.baseUrl}/portfolio/charts/allocation`, {
      params: this.buildPortfolioParams(symbols, weights, '1y')
    });
  }

  getPortfolioScenario(
    symbols: string[],
    values: number[],
    scenarioSymbol: string,
    additionalQuantity: number,
    period: string,
    cashValue: number,
    volatilityWindow: number,
  ): Observable<PortfolioScenario> {
    let params = new HttpParams()
      .set('scenario_symbol', scenarioSymbol)
      .set('additional_quantity', additionalQuantity)
      .set('period', period)
      .set('cash_value', cashValue)
      .set('volatility_window', volatilityWindow);
    symbols.forEach((symbol, index) => {
      params = params.append('symbols', symbol).append('values', values[index]);
    });
    return this.http.get<PortfolioScenario>(`${this.baseUrl}/portfolio/scenario`, { params });
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

  private buildPortfolioParams(
    symbols: string[],
    weights: number[],
    period: string,
    volatilityWindow?: number,
    transactions: PortfolioTransaction[] = [],
  ): HttpParams {
    let params = new HttpParams().set('period', period);
    symbols.forEach((symbol, index) => {
      params = params.append('symbols', symbol).append('weights', weights[index]);
    });
    if (volatilityWindow !== undefined) params = params.set('volatility_window', volatilityWindow);
    transactions.forEach((transaction) => {
      params = params
        .append('transaction_symbols', transaction.symbol)
        .append('transaction_sides', transaction.side)
        .append('transaction_quantities', transaction.quantity)
        .append('transaction_dates', transaction.createdOn);
    });
    return params;
  }
}
