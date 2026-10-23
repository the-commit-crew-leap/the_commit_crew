import { of, throwError } from 'rxjs';

import { AuthApiService } from './auth-api.service';
import { AuthSessionService } from './auth-session.service';
import { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse, ValidateResponse } from './auth.models';
import { TokenStorageService } from './token-storage.service';

describe('AuthSessionService', () => {
  let service: AuthSessionService;
  let authApi: jest.Mocked<AuthApiService>;
  let tokenStorage: jest.Mocked<TokenStorageService>;

  const loginPayload: LoginRequest = { username: 'alice', password: 'Password123!' };
  const loginResponse: LoginResponse = {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresIn: 3600,
    username: 'alice.server',
    accountId: 'ACC-3F842FBE',
    role: 'USER'
  };

  beforeEach(() => {
    localStorage.clear();
    authApi = {
      login: jest.fn().mockReturnValue(of(loginResponse)),
      validate: jest.fn(),
      register: jest.fn()
    } as unknown as jest.Mocked<AuthApiService>;
    tokenStorage = {
      getAccountId: jest.fn().mockReturnValue(null),
      getAccessToken: jest.fn().mockReturnValue(null),
      getRole: jest.fn().mockReturnValue(null),
      isExpired: jest.fn().mockReturnValue(true),
      saveSession: jest.fn(),
      clearSession: jest.fn()
    } as unknown as jest.Mocked<TokenStorageService>;
    service = new AuthSessionService(authApi, tokenStorage);
  });

  it('initializes account identity from token storage', () => {
    tokenStorage.getAccountId.mockReturnValue('ACC-INITIAL');
    const initialized = new AuthSessionService(authApi, tokenStorage);

    expect(initialized.accountId()).toBe('ACC-INITIAL');
    expect(initialized.username()).toBeNull();
  });

  it('stores login details and honors remember-me selection', () => {
    const now = Date.now();
    const nowSpy = jest.spyOn(Date, 'now').mockReturnValue(now);
    service.login(loginPayload, true).subscribe();

    expect(authApi.login).toHaveBeenCalledWith(loginPayload);
    expect(service.accountId()).toBe('ACC-3F842FBE');
    expect(service.username()).toBe('alice.server');
    expect(localStorage.getItem('auth.accountId')).toBe('ACC-3F842FBE');
    expect(tokenStorage.saveSession).toHaveBeenCalledWith({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      expiresAt: now + 3_600_000,
      accountId: 'ACC-3F842FBE',
      role: 'USER'
    }, true);
    nowSpy.mockRestore();
  });

  it('falls back to the submitted username when the login response username is empty', () => {
    authApi.login.mockReturnValue(of({ ...loginResponse, username: '' }));
    service.login(loginPayload).subscribe();

    expect(service.username()).toBe('alice');
    expect(tokenStorage.saveSession).toHaveBeenCalledWith(
      expect.objectContaining({ accessToken: 'access-token', accountId: 'ACC-3F842FBE' }),
      false
    );
  });

  it('accepts valid Google callback fragments and rejects incomplete or malformed fragments', () => {
    const now = Date.now();
    const nowSpy = jest.spyOn(Date, 'now').mockReturnValue(now);
    const validFragment = '#accessToken=google-access&refreshToken=google-refresh&expiresIn=60&accountId=ACC-GOOGLE&role=USER&username=google-user';

    expect(service.completeGoogleLogin(validFragment, true)).toBe(true);
    expect(service.accountId()).toBe('ACC-GOOGLE');
    expect(service.username()).toBe('google-user');
    expect(localStorage.getItem('auth.accountId')).toBe('ACC-GOOGLE');
    expect(tokenStorage.saveSession).toHaveBeenCalledWith({
      accessToken: 'google-access',
      refreshToken: 'google-refresh',
      expiresAt: now + 60_000,
      accountId: 'ACC-GOOGLE',
      role: 'USER'
    }, true);

    tokenStorage.saveSession.mockClear();
    expect(service.completeGoogleLogin('accessToken=only-one')).toBe(false);
    expect(service.completeGoogleLogin('accessToken=a&refreshToken=b&expiresIn=nope&accountId=id&role=USER')).toBe(false);
    expect(tokenStorage.saveSession).not.toHaveBeenCalled();
    nowSpy.mockRestore();
  });

  it('completes Google login without an optional username', () => {
    expect(service.completeGoogleLogin(
      'accessToken=a&refreshToken=b&expiresIn=60&accountId=ACC-GOOGLE&role=USER'
    )).toBe(true);

    expect(service.username()).toBeNull();
  });

  it('logs out when validation has no token or the stored session is expired', () => {
    service.validateSession().subscribe();
    expect(tokenStorage.clearSession).toHaveBeenCalledTimes(1);

    tokenStorage.clearSession.mockClear();
    tokenStorage.getAccessToken.mockReturnValue('access-token');
    tokenStorage.isExpired.mockReturnValue(true);
    service.validateSession().subscribe();
    expect(authApi.validate).not.toHaveBeenCalled();
    expect(tokenStorage.clearSession).toHaveBeenCalledTimes(1);
  });

  it('updates username for valid sessions and logs out for invalid sessions', () => {
    tokenStorage.getAccessToken.mockReturnValue('access-token');
    tokenStorage.isExpired.mockReturnValue(false);
    authApi.validate.mockReturnValue(of({
      valid: true,
      username: 'verified-user',
      accountId: 'ACC-3F842FBE',
      role: 'USER'
    } satisfies ValidateResponse));

    service.validateSession().subscribe();
    expect(service.username()).toBe('verified-user');
    expect(tokenStorage.clearSession).not.toHaveBeenCalled();

    authApi.validate.mockReturnValue(of({
      valid: false,
      username: '',
      accountId: null,
      role: null
    }));
    service.validateSession().subscribe();
    expect(tokenStorage.clearSession).toHaveBeenCalledTimes(1);
    expect(service.username()).toBeNull();
  });

  it('logs out and completes when session validation fails', () => {
    tokenStorage.getAccessToken.mockReturnValue('access-token');
    tokenStorage.isExpired.mockReturnValue(false);
    authApi.validate.mockReturnValue(throwError(() => new Error('network error')));

    let completed = false;
    service.validateSession().subscribe({ complete: () => { completed = true; } });

    expect(completed).toBe(true);
    expect(tokenStorage.clearSession).toHaveBeenCalled();
    expect(service.accountId()).toBeNull();
  });

  it('clears storage and identity during logout', () => {
    service.accountId.set('ACC-3F842FBE');
    service.username.set('alice');
    localStorage.setItem('auth.accountId', 'ACC-3F842FBE');

    service.logout();

    expect(tokenStorage.clearSession).toHaveBeenCalled();
    expect(localStorage.getItem('auth.accountId')).toBeNull();
    expect(service.accountId()).toBeNull();
    expect(service.username()).toBeNull();
  });

  it('exposes tokens, account identity, role, and expires expired sessions', () => {
    tokenStorage.getAccessToken.mockReturnValue('access-token');
    tokenStorage.getAccountId.mockReturnValue('ACC-3F842FBE');
    tokenStorage.getRole.mockReturnValue('USER');
    tokenStorage.isExpired.mockReturnValue(false);

    expect(service.getAccessToken()).toBe('access-token');
    expect(service.getAccountId()).toBe('ACC-3F842FBE');
    expect(service.getRole()).toBe('USER');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accountId()).toBe('ACC-3F842FBE');

    tokenStorage.isExpired.mockReturnValue(true);
    expect(service.getAccessToken()).toBeNull();
    expect(service.getAccountId()).toBeNull();
    expect(service.getRole()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(tokenStorage.clearSession).toHaveBeenCalled();
  });

  it('delegates registration to the auth API', () => {
    const payload: RegisterRequest = {
      username: 'alice',
      email: 'alice@example.com',
      fullName: 'Alice Example',
      password: 'Password123!'
    };
    const response: RegisterResponse = {
      id: 'user-id',
      username: 'alice',
      email: payload.email,
      accountId: 'ACC-3F842FBE',
      createdAt: '2026-10-23T00:00:00Z'
    };
    authApi.register.mockReturnValue(of(response));

    let result: RegisterResponse | undefined;
    service.register(payload).subscribe((value) => { result = value; });

    expect(authApi.register).toHaveBeenCalledWith(payload);
    expect(result).toEqual(response);
  });
});
