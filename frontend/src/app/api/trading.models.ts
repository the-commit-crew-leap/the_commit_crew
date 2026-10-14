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
}