import { JwtService } from '@nestjs/jwt';
import { TokenService } from './token.service';
import { JWT_ISSUER } from './constants';

describe('TokenService', () => {
  const secret = 'test-secret';
  let tokenService: TokenService;
  let jwtService: JwtService;

  beforeEach(() => {
    jwtService = new JwtService({ secret });
    tokenService = new TokenService(jwtService);
  });

  it('adds the expected issuer to access and refresh tokens', () => {
    const result = tokenService.issue('alice', 'ACC-123', 'CLIENT');

    const accessPayload = jwtService.decode(result.accessToken) as Record<string, unknown>;
    const refreshPayload = jwtService.decode(result.refreshToken) as Record<string, unknown>;

    expect(accessPayload.iss).toBe(JWT_ISSUER);
    expect(refreshPayload.iss).toBe(JWT_ISSUER);
  });

  it('accepts a token with the correct issuer', () => {
    const { accessToken } = tokenService.issue('alice');

    expect(tokenService.verify(accessToken)).toBe(true);
  });

  it('rejects a token with the wrong issuer', () => {
    const token = jwtService.sign(
      { username: 'alice', sub: 'alice' },
      {
        issuer: 'wrong-issuer',
        expiresIn: '15m',
      },
    );

    expect(tokenService.verify(token)).toBe(false);
  });

  it('rejects an expired token', () => {
    const token = jwtService.sign(
      { username: 'alice', sub: 'alice' },
      {
        issuer: JWT_ISSUER,
        expiresIn: '-1s',
      },
    );

    expect(tokenService.verify(token)).toBe(false);
  });

  it('rejects a token signed with the wrong secret', () => {
    const tokenWithWrongSecret = new JwtService({
      secret: 'wrong-secret',
    }).sign(
      { username: 'alice', sub: 'alice' },
      {
        issuer: JWT_ISSUER,
        expiresIn: '15m',
      },
    );

    expect(tokenService.verify(tokenWithWrongSecret)).toBe(false);
  });

  it('includes exactly the approved JWT claims', () => {
    const result = tokenService.issue('alice', 'ACC-123', 'CLIENT');

    const accessPayload = jwtService.decode(result.accessToken) as Record<string, unknown>;
    const refreshPayload = jwtService.decode(result.refreshToken) as Record<string, unknown>;

    const expectedClaims = [
      'username',
      'sub',
      'accountId',
      'role',
      'iss',
      'iat',
      'exp',
    ].sort();

    expect(Object.keys(accessPayload).sort()).toEqual(expectedClaims);
    expect(Object.keys(refreshPayload).sort()).toEqual(expectedClaims);
  });

  it('supports tokens without optional account metadata', () => {
    const result = tokenService.issue('alice');

    const payload = jwtService.decode(result.accessToken) as Record<string, unknown>;

    expect(payload.username).toBe('alice');
    expect(payload.sub).toBe('alice');
    expect(payload.accountId).toBeUndefined();
    expect(payload.role).toBeUndefined();
  });

  it('supports an account ID without a role', () => {
    const result = tokenService.issue('alice', 'ACC-123');

    const payload = jwtService.decode(result.accessToken) as Record<string, unknown>;

    expect(payload.accountId).toBe('ACC-123');
    expect(payload.role).toBeUndefined();
  });

  it('sets the refresh token expiry to seven days', () => {
    const result = tokenService.issue('alice', 'ACC-123', 'CLIENT');

    const refreshPayload = jwtService.decode(result.refreshToken) as {
      iat: number;
      exp: number;
    };

    const sevenDaysInSeconds = 7 * 24 * 60 * 60;

    expect(refreshPayload.exp - refreshPayload.iat).toBe(sevenDaysInSeconds);
  });
});