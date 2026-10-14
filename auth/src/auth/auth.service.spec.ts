import { UnauthorizedException, ConflictException } from '@nestjs/common';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let mockTokenService: {
    issue: jest.Mock;
    verify: jest.Mock;
    decode: jest.Mock;
  };
  let mockUsersRepository: {
    findByUsername: jest.Mock;
    findByEmail: jest.Mock;
    createUserWithCredentialAndTradingLink: jest.Mock;
    findByUsernameWithCredentialAndTradingAccounts: jest.Mock;
    resetFailedLoginAttempts: jest.Mock;
    updateLastLogin: jest.Mock;
    incrementFailedLoginAttempts: jest.Mock;
  };
  let mockDatabaseService: {
    createAccount: jest.Mock;
  };

  beforeEach(() => {
    process.env.BCRYPT_COST_FACTOR = '4';

    mockTokenService = {
      issue: jest.fn().mockReturnValue({
        accessToken: 'mock-access-token',
        refreshToken: 'mock-refresh-token',
        expiresIn: 900,
      }),
      verify: jest.fn().mockReturnValue(true),
      decode: jest.fn(),
    };

    mockUsersRepository = {
      findByUsername: jest.fn(),
      findByEmail: jest.fn(),
      createUserWithCredentialAndTradingLink: jest.fn(),
      findByUsernameWithCredentialAndTradingAccounts: jest.fn(),
      resetFailedLoginAttempts: jest.fn(),
      updateLastLogin: jest.fn(),
      incrementFailedLoginAttempts: jest.fn(),
    };

    mockDatabaseService = {
      createAccount: jest.fn(),
    };

    service = new AuthService(
      mockTokenService as any,
      mockUsersRepository as any,
      mockDatabaseService as any,
    );
  });

  afterEach(() => {
    delete process.env.BCRYPT_COST_FACTOR;
  });

  it('registers a user and links a trading account', async () => {
    mockUsersRepository.findByUsername.mockResolvedValue(null);
    mockUsersRepository.findByEmail.mockResolvedValue(null);
    mockUsersRepository.createUserWithCredentialAndTradingLink.mockResolvedValue({
      userId: 101,
      username: 'alice',
      email: 'alice@example.com',
      createdAt: new Date('2026-01-01T00:00:00Z'),
    });

    const result = await service.register({
      username: 'alice',
      email: 'alice@example.com',
      fullName: 'Alice Smith',
      password: 'SecurePass123!',
    });

    expect(mockDatabaseService.createAccount).toHaveBeenCalledTimes(1);
    expect(mockUsersRepository.createUserWithCredentialAndTradingLink).toHaveBeenCalledWith(
      expect.objectContaining({
        username: 'alice',
        email: 'alice@example.com',
        role: 'CLIENT',
      }),
    );
    expect(result.username).toBe('alice');
    expect(result.accountId).toMatch(/^ACC-/);
  });

  it('rejects an email that already exists with different casing', async () => {
    mockUsersRepository.findByUsername.mockResolvedValue(null);
    mockUsersRepository.findByEmail.mockResolvedValue({
      userId: 100,
      username: 'existinguser',
      email: 'TeSt@gmail.com',
    });

    await expect(
      service.register({
        username: 'newuser',
        email: 'test@gmail.com',
        fullName: 'New User',
        password: 'SecurePass123!',
      }),
    ).rejects.toThrow('Email address already exists');

    await expect(
      service.register({
        username: 'anotheruser',
        email: 'TEST@GMAIL.COM',
        fullName: 'Another User',
        password: 'SecurePass123!',
      }),
    ).rejects.toThrow(ConflictException);

    expect(mockDatabaseService.createAccount).not.toHaveBeenCalled();
  });

  it('issues login tokens with linked accountId and role', async () => {
    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      userId: 200,
      status: 'ACTIVE',
      credential: {
        passwordHash: '$2b$04$N4Pt4fDNHCPW5q4YHLeA1e7Q9n2mI9n0jSjrRwzLx4grt6t6wJQeK',
        failedLoginAttempts: 0,
        lockedUntil: null,
      },
      tradingAccounts: [{ accountId: 'ACC-12345678', role: 'CLIENT' }],
    });

    // Password is SecurePass123! for the hash above with low work factor in test environment.
    const bcrypt = await import('bcrypt');
    const hash = await bcrypt.hash('SecurePass123!', 4);
    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      userId: 200,
      status: 'ACTIVE',
      credential: {
        passwordHash: hash,
        failedLoginAttempts: 0,
        lockedUntil: null,
      },
      tradingAccounts: [{ accountId: 'ACC-12345678', role: 'CLIENT' }],
    });

    await service.login({ username: 'alice', password: 'SecurePass123!' });

    expect(mockTokenService.issue).toHaveBeenCalledWith('alice', 'ACC-12345678', 'CLIENT');
  });

  it('rejects login when user has no linked trading account', async () => {
    const bcrypt = await import('bcrypt');
    const hash = await bcrypt.hash('SecurePass123!', 4);

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      userId: 201,
      status: 'ACTIVE',
      credential: {
        passwordHash: hash,
        failedLoginAttempts: 0,
        lockedUntil: null,
      },
      tradingAccounts: [],
    });

    await expect(
      service.login({ username: 'alice', password: 'SecurePass123!' }),
    ).rejects.toThrow(UnauthorizedException);
  });

  it('reactivates a suspended user after lock expiry and allows login', async () => {
    const bcrypt = await import('bcrypt');
    const hash = await bcrypt.hash('SecurePass123!', 4);
    const expiredLock = new Date(Date.now() - 60_000);

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      userId: 202,
      status: 'SUSPENDED',
      credential: {
        passwordHash: hash,
        failedLoginAttempts: 5,
        lockedUntil: expiredLock,
      },
      tradingAccounts: [{ accountId: 'ACC-12345678', role: 'CLIENT' }],
    });

    await service.login({ username: 'alice', password: 'SecurePass123!' });

    expect(mockUsersRepository.resetFailedLoginAttempts).toHaveBeenCalledWith(202);
    expect(mockTokenService.issue).toHaveBeenCalledWith('alice', 'ACC-12345678', 'CLIENT');
  });

  it('validate falls back to DB mapping when token has no accountId', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({ username: 'alice' });
    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      tradingAccounts: [{ accountId: 'ACC-ABCDEF12', role: 'CLIENT' }],
    });

    const result = await service.validate('some-token');

    expect(result).toEqual({
      username: 'alice',
      accountId: 'ACC-ABCDEF12',
      role: 'CLIENT',
      valid: true,
    });
  });
});