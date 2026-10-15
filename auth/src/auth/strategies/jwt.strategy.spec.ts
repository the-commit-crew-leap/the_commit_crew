import { JwtStrategy } from './jwt.strategy';

describe('JwtStrategy', () => {
  it('maps a validated JWT payload to the authenticated user', () => {
    const strategy = new JwtStrategy();

    expect(strategy.validate({
      sub: 'alice',
      username: 'alice',
    })).toEqual({
      userId: 'alice',
      username: 'alice',
    });
  });
});