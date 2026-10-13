import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order } from '../models/order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderHistoryService {
  private readonly apiBaseUrl = `${window.location.protocol}//${window.location.hostname}:8081`;

  constructor(private readonly http: HttpClient) {}

  getOrderHistory(accountId: string): Observable<Order[]> {
    return this.http.get<Order[]>(
      `${this.apiBaseUrl}/accounts/${accountId}/orders`
    );
  }
}