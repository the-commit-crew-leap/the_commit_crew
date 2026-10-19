import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Order } from '../models/order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderHistoryService {

  constructor(private readonly http: HttpClient) {}

  getOrderHistory(accountId: string): Observable<Order[]> {
    return this.http.get<Order[]>(
      `${environment.appApiBaseUrl}/accounts/${accountId}/orders`
    );
  }
}