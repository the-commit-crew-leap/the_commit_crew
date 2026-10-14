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
});