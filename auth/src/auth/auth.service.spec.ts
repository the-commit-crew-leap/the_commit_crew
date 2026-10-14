import { UnauthorizedException, ConflictException, BadRequestException } from '@nestjs/common';
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
    deleteAccount: jest.Mock;
    closeAccount: jest.Mock;
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
      deleteAccount: jest.fn(),
      closeAccount: jest.fn(),
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
    ).rejects.toThrow('Username or email already exists');

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

    expect(mockUsersRepository.resetFailedLoginAttempts).toHaveBeenCalledWith(200);
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

  it('increments failed login attempts', async () => {
    const bcrypt = await import('bcrypt');
    const passwordHash = await bcrypt.hash('CorrectPass123!', 4);

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      userId: 300,
      status: 'ACTIVE',
      credential: {
        passwordHash,
        failedLoginAttempts: 0,
        lockedUntil: null,
      },
      tradingAccounts: [{ accountId: 'ACC-300', role: 'CLIENT' }],
    });

    await expect(
      service.login({ username: 'alice', password: 'WrongPass123!' }),
    ).rejects.toThrow(UnauthorizedException);

    expect(mockUsersRepository.incrementFailedLoginAttempts).toHaveBeenCalledWith(300);
  });

  it('refreshes a token using database account fallback', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({
      username: 'alice',
    });

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts
      .mockResolvedValue({
        username: 'alice',
        tradingAccounts: [
          {
            accountId: 'ACC-FALLBACK',
            role: 'CLIENT',
          },
        ],
      });

    await service.refresh('refresh-token');

    expect(
      mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts,
    ).toHaveBeenCalledWith('alice');

    expect(mockTokenService.issue).toHaveBeenCalledWith(
      'alice',
      'ACC-FALLBACK',
      'CLIENT',
    );
  });

  it('rejects user deletion because it is not implemented', async () => {
    await expect(
      service.deleteUser(999),
    ).rejects.toThrow('User deletion is not implemented yet');
  });

  it('rejects an invalid access token', async () => {
    mockTokenService.verify.mockReturnValue(false);

    await expect(
      service.validate('invalid-access-token'),
    ).rejects.toThrow('Invalid token');

    expect(mockTokenService.decode).not.toHaveBeenCalled();
  });

  it('uses closeAccount when compensation deletion fails', async () => {
    mockUsersRepository.findByUsername.mockResolvedValue(null);
    mockUsersRepository.findByEmail.mockResolvedValue(null);
    mockDatabaseService.createAccount.mockResolvedValue(undefined);
    mockUsersRepository.createUserWithCredentialAndTradingLink
      .mockRejectedValue(new Error('user creation failed'));
    mockDatabaseService.deleteAccount
      .mockRejectedValue(new Error('delete failed'));
    mockDatabaseService.closeAccount.mockResolvedValue(true);

    await expect(
      service.register({
        username: 'alice',
        email: 'alice@example.com',
        fullName: 'Alice',
        password: 'SecurePass123!',
      }),
    ).rejects.toThrow('Registration failed. Please try again.');

    expect(mockDatabaseService.deleteAccount).toHaveBeenCalled();
    expect(mockDatabaseService.closeAccount).toHaveBeenCalled();
  });

  it('continues when both compensation methods fail', async () => {
    mockUsersRepository.findByUsername.mockResolvedValue(null);
    mockUsersRepository.findByEmail.mockResolvedValue(null);
    mockDatabaseService.createAccount.mockResolvedValue(undefined);
    mockUsersRepository.createUserWithCredentialAndTradingLink
      .mockRejectedValue(new Error('user creation failed'));
    mockDatabaseService.deleteAccount
      .mockRejectedValue(new Error('delete failed'));
    mockDatabaseService.closeAccount
      .mockRejectedValue(new Error('close failed'));

    await expect(
      service.register({
        username: 'bob',
        email: 'bob@example.com',
        fullName: 'Bob',
        password: 'SecurePass123!',
      }),
    ).rejects.toThrow('Registration failed. Please try again.');

    expect(mockDatabaseService.deleteAccount).toHaveBeenCalled();
    expect(mockDatabaseService.closeAccount).toHaveBeenCalled();
  });

  it('rejects login while the account is locked', async () => {
    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      userId: 301,
      status: 'SUSPENDED',
      credential: {
        passwordHash: 'unused',
        failedLoginAttempts: 5,
        lockedUntil: new Date(Date.now() + 15 * 60 * 1000),
      },
      tradingAccounts: [{ accountId: 'ACC-301', role: 'CLIENT' }],
    });

    await expect(
      service.login({ username: 'alice', password: 'CorrectPass123!' }),
    ).rejects.toThrow('Account is locked');
  });

    it('rejects a duplicate username', async () => {
    mockUsersRepository.findByUsername.mockResolvedValue({
      userId: 400,
      username: 'alice',
    });

    await expect(
      service.register({
        username: 'alice',
        email: 'alice@example.com',
        fullName: 'Alice',
        password: 'SecurePass123!',
      }),
    ).rejects.toThrow('Username or email already exists');

    expect(mockDatabaseService.createAccount).not.toHaveBeenCalled();
  });

  it('rejects an unknown username with invalid credentials', async () => {
    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts
      .mockResolvedValue(null);

    await expect(
      service.login({
        username: 'unknown-user',
        password: 'WrongPass123!',
      }),
    ).rejects.toThrow('Invalid credentials');
  });

  it('rejects an incorrect password and increments failed attempts', async () => {
    const bcrypt = await import('bcrypt');
    const passwordHash = await bcrypt.hash('CorrectPass123!', 4);

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts
      .mockResolvedValue({
        userId: 401,
        status: 'ACTIVE',
        credential: {
          passwordHash,
          failedLoginAttempts: 0,
          lockedUntil: null,
        },
        tradingAccounts: [{ accountId: 'ACC-401', role: 'CLIENT' }],
      });

    await expect(
      service.login({
        username: 'alice',
        password: 'WrongPass123!',
      }),
    ).rejects.toThrow('Invalid credentials');

    expect(mockUsersRepository.incrementFailedLoginAttempts)
      .toHaveBeenCalledWith(401);
  });

  it('rejects an inactive user', async () => {
    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts
      .mockResolvedValue({
        userId: 402,
        status: 'SUSPENDED',
        credential: {
          passwordHash: 'unused',
          failedLoginAttempts: 0,
          lockedUntil: null,
        },
        tradingAccounts: [{ accountId: 'ACC-402', role: 'CLIENT' }],
      });

    await expect(
      service.login({
        username: 'alice',
        password: 'CorrectPass123!',
      }),
    ).rejects.toThrow('User account is not active');
  });

  it('rejects an invalid refresh token', async () => {
    mockTokenService.verify.mockReturnValue(false);

    await expect(
      service.refresh('invalid-refresh-token'),
    ).rejects.toThrow('Invalid refresh token');

    expect(mockTokenService.decode).not.toHaveBeenCalled();
  });

  it('refreshes a valid refresh token', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({
      username: 'alice',
      accountId: 'ACC-403',
      role: 'CLIENT',
    });

    await service.refresh('valid-refresh-token');

    expect(mockTokenService.issue).toHaveBeenCalledWith(
      'alice',
      'ACC-403',
      'CLIENT',
    );
  });

  it('rejects a refresh token with an invalid payload', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({
      accountId: 'ACC-404',
    });

    await expect(
      service.refresh('invalid-payload-token'),
    ).rejects.toThrow('Invalid refresh token payload');
  });

  it('rejects validation for an invalid token payload', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({
      username: 'alice',
    });

    await expect(
      service.validate('invalid-payload-token'),
    ).rejects.toThrow('Invalid token payload');
  });

  it('rejects validation for an inactive user', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({
      username: 'alice',
      accountId: 'ACC-405',
    });

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts
      .mockResolvedValue({
        username: 'alice',
        status: 'SUSPENDED',
        tradingAccounts: [
          { accountId: 'ACC-405', role: 'CLIENT' },
        ],
      });

    await expect(
      service.validate('inactive-user-token'),
    ).rejects.toThrow('Session is no longer valid');
  });

  it('rejects validation for an unlinked account', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({
      username: 'alice',
      accountId: 'ACC-406',
    });

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts
      .mockResolvedValue({
        username: 'alice',
        status: 'ACTIVE',
        tradingAccounts: [
          { accountId: 'ACC-DIFFERENT', role: 'CLIENT' },
        ],
      });

    await expect(
      service.validate('unlinked-account-token'),
    ).rejects.toThrow('Session is no longer valid');
  });

  it('compensates when registration fails after creating the account', async () => {
    mockUsersRepository.findByUsername.mockResolvedValue(null);
    mockUsersRepository.findByEmail.mockResolvedValue(null);
    mockDatabaseService.createAccount.mockResolvedValue(undefined);
    mockUsersRepository.createUserWithCredentialAndTradingLink
      .mockRejectedValue(new Error('database failure'));
    mockDatabaseService.deleteAccount.mockResolvedValue(undefined);

    await expect(
      service.register({
        username: 'alice',
        email: 'alice@example.com',
        fullName: 'Alice',
        password: 'SecurePass123!',
      }),
    ).rejects.toThrow('Registration failed. Please try again.');

    expect(mockDatabaseService.createAccount).toHaveBeenCalledTimes(1);
    expect(mockDatabaseService.deleteAccount).toHaveBeenCalledWith(
      expect.stringMatching(/^ACC-/),
    );
    expect(mockDatabaseService.closeAccount).not.toHaveBeenCalled();
  });

  it('validates an active user with a linked trading account', async () => {
    mockTokenService.verify.mockReturnValue(true);
    mockTokenService.decode.mockReturnValue({
      username: 'alice',
      accountId: 'ACC-ABCDEF12',
    });

    mockUsersRepository.findByUsernameWithCredentialAndTradingAccounts.mockResolvedValue({
      username: 'alice',
      status: 'ACTIVE',
      tradingAccounts: [
        {
          accountId: 'ACC-ABCDEF12',
          role: 'CLIENT',
        },
      ],
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