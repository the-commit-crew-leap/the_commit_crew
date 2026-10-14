export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  fullName: string;
  password: string;
}

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  accountId: string;
  role: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  accountId: string;
  role: string;
}

export interface RegisterResponse {
  id: string;
  username: string;
  email: string;
  accountId: string;
  createdAt: string;
}

export interface ApiSuccess<T> {
  status: 'SUCCESS';
  data: T;
}

export interface ApiError {
  status: 'ERROR';
  message: string;
  data: null;
}