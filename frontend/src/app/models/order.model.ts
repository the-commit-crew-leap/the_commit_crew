export type OrderStatus = 'NEW' | 'FILLED' | 'REJECTED' | 'CANCELLED';
export type OrderSide = 'BUY' | 'SELL';

export interface Order {
  orderId: string;
  accountId: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  status: OrderStatus;
  createdOn: string;
}