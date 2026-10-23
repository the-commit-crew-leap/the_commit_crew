import { TokenStorageService } from './token-storage.service';

describe('TokenStorageService', () => {
  let service: TokenStorageService;
  const sessionKey = 'auth_session';

  const session = {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresAt: 5000,
    accountId: 'ACC-3F842FBE',
    role: 'USER'
  };

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    service = new TokenStorageService();
  });

  it('stores remembered sessions in local storage and clears prior copies', () => {
    sessionStorage.setItem(sessionKey, 'old-session');
    service.saveSession(session, true);

    expect(sessionStorage.getItem(sessionKey)).toBeNull();
    expect(JSON.parse(localStorage.getItem(sessionKey)!)).toEqual(session);
    expect(service.getSession()).toEqual(session);
  });

  it('stores non-remembered sessions in session storage', () => {
    localStorage.setItem(sessionKey, 'old-session');
    service.saveSession(session, false);

    expect(localStorage.getItem(sessionKey)).toBeNull();
    expect(JSON.parse(sessionStorage.getItem(sessionKey)!)).toEqual(session);
  });

  it('prefers the session-storage copy when both storage locations contain data', () => {
    localStorage.setItem(sessionKey, JSON.stringify({ ...session, accessToken: 'local-token' }));
    sessionStorage.setItem(sessionKey, JSON.stringify({ ...session, accessToken: 'session-token' }));

    expect(service.getAccessToken()).toBe('session-token');
  });

  it('clears malformed session data and returns null values for missing data', () => {
    localStorage.setItem(sessionKey, '{malformed');
    sessionStorage.setItem(sessionKey, '{malformed');

    expect(service.getSession()).toBeNull();
    expect(localStorage.getItem(sessionKey)).toBeNull();
    expect(sessionStorage.getItem(sessionKey)).toBeNull();
    expect(service.getAccessToken()).toBeNull();
    expect(service.getExpiresAt()).toBeNull();
    expect(service.getAccountId()).toBeNull();
    expect(service.getRole()).toBeNull();
    expect(service.isExpired(0)).toBe(true);
  });

  it('treats the expiry instant as expired and supports a supplied clock', () => {
    service.saveSession(session, false);

    expect(service.isExpired(4999)).toBe(false);
    expect(service.isExpired(5000)).toBe(true);
    expect(service.isExpired(6000)).toBe(true);
  });

  it('treats a zero expiry as expired', () => {
    service.saveSession({ ...session, expiresAt: 0 }, false);

    expect(service.isExpired(0)).toBe(true);
  });
});
