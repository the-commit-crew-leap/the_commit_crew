export interface Money {
    amount: string;
    currency?: string;
}

export interface AccountResponse {
    accountId: string;
    status: 'ACTIVE' | 'CLOSED' | 'SUSPENDED';
    cashBalance: Money;
}

export interface BalanceResponse {
    accountId: string;
    cashBalance: Money;
}

export interface PositionResponse {
    symbol: string;
    quantity: number;
    averageCost: number;
    currentPrice: number | null;
    marketValue: number | null;
    unrealizedPnL: number | null;
    unrealizedPnLPercent: number | null;
    priceSource?: string | null;
    priceUpdatedAt?: string | null;
    live?: boolean;
}

export interface TradingApiError {
    errorCode: string;
    message: string;
}

export type OrderSide = 'BUY' | 'SELL';

export interface PlaceOrderRequest {
    accountId: string;
    symbol: string;
    side: OrderSide;
    quantity: number;
}

export interface OrderResponse {
    orderId: string;
    accountId: string;
    symbol: string;
    side: OrderSide;
    quantity: number;
    price: number;
    status: 'NEW' | 'FILLED' | 'REJECTED' | 'CANCELLED';
    createdOn: string;
    assetClass?: 'EQUITY' | 'BOND' | 'FUND' | 'CASH';
}

export interface DepositRequest {
    amount: number;
}

export interface DepositResponse {
    accountId: string;
    transactionType: 'DEPOSIT';
    amount: number;
    newBalance: Money;
    timestamp: string;
}

export interface InstrumentResponse {
    symbol: string;
    name: string;
    assetClass: string;
    currency: string;
    tradable: boolean;
}

export interface PriceResponse {
    symbol: string;
    price: number;
    source: string;
    live: boolean;
    updatedAt: string;
}

export interface IntradayPriceResponse {
    symbol: string;
    sessionDate: string;
    source: string;
    live: boolean;
    updatedAt: string | null;
    bars: IntradayBar[];
}

export interface IntradayBar {
    timestamp: string;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
}

export interface MarketTickerResponse {
    symbol: string;
    price: number | null;
    changePercent: number | null;
    source: string | null;
    live: boolean;
    updatedAt: string | null;
}