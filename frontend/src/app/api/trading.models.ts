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