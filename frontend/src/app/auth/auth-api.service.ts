import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ApiSuccess,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  ValidateResponse
} from './auth.models';

@Injectable({
  providedIn: 'root'
})
export class AuthApiService {
  private readonly baseUrl = environment.authApiBaseUrl;

  get googleLoginUrl(): string {
    return `${this.baseUrl}/google`;
  }

  constructor(private readonly http: HttpClient) {}

  login(payload: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<ApiSuccess<LoginResponse>>(`${this.baseUrl}/login`, payload)
      .pipe(map((res) => res.data));
  }

  register(payload: RegisterRequest): Observable<RegisterResponse> {
    return this.http
      .post<ApiSuccess<RegisterResponse>>(`${this.baseUrl}/register`, payload)
      .pipe(map((res) => res.data));
  }

  validate(token: string): Observable<ValidateResponse> {
    return this.http
      .post<ApiSuccess<ValidateResponse>>(
        `${this.baseUrl}/validate`,
        { token }
      )
      .pipe(map((response) => response.data));
  }
}