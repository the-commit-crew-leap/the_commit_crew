import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AuthController } from '../src/auth/auth.controller';
import { AuthService } from '../src/auth/auth.service';
import { TokenService } from '../src/auth/token.service';
import { UsersRepository } from '../src/auth/repositories/users.repository';
import { DatabaseService } from '../src/auth/database.service';
import { HttpExceptionFilter } from '../src/common/exceptions.filter';

describe('Register Compensation (e2e)', () => {
  let app: INestApplication<App>;

  const mockTokenService = {
    issue: jest.fn(),
    verify: jest.fn(),
    decode: jest.fn(),
  };

  const mockUsersRepository = {
    findByUsername: jest.fn(),
    createUserWithCredentialAndTradingLink: jest.fn(),
    findByUsernameWithCredentialAndTradingAccounts: jest.fn(),
    resetFailedLoginAttempts: jest.fn(),
    updateLastLogin: jest.fn(),
    incrementFailedLoginAttempts: jest.fn(),
  };

  const mockDatabaseService = {
    createAccount: jest.fn(),
    deleteAccount: jest.fn(),
    closeAccount: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    process.env.BCRYPT_COST_FACTOR = '4';

    // Prevent onModuleInit from creating the default test user during app init.
    mockUsersRepository.findByUsername.mockImplementation(async (username: string) => {
      if (username === 'testuser') {
        return { userId: 1, username: 'testuser' };
      }
      return null;
    });

    const moduleFixture: TestingModule = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        AuthService,
        { provide: TokenService, useValue: mockTokenService },
        { provide: UsersRepository, useValue: mockUsersRepository },
        { provide: DatabaseService, useValue: mockDatabaseService },
      ],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.useGlobalFilters(new HttpExceptionFilter());
    await app.init();
  });

  afterEach(async () => {
    delete process.env.BCRYPT_COST_FACTOR;
    await app.close();
  });

  it('compensates by deleting trading account when auth persistence fails', async () => {
    mockDatabaseService.createAccount.mockResolvedValue(true);
    mockUsersRepository.createUserWithCredentialAndTradingLink.mockRejectedValue(
      new Error('Auth DB transaction failed'),
    );
    mockDatabaseService.deleteAccount.mockResolvedValue(true);

    const response = await request(app.getHttpServer())
      .post('/api/auth/register')
      .send({
        username: 'alice',
        email: 'alice@example.com',
        fullName: 'Alice Smith',
        password: 'SecurePass123!',
      })
      .expect(400);

    expect(response.body).toEqual({
      status: 'ERROR',
      message: 'Registration failed. Please try again.',
      data: null,
    });

    expect(mockDatabaseService.createAccount).toHaveBeenCalledTimes(1);
    expect(mockUsersRepository.createUserWithCredentialAndTradingLink).toHaveBeenCalledTimes(1);
    expect(mockDatabaseService.deleteAccount).toHaveBeenCalledTimes(1);
    expect(mockDatabaseService.closeAccount).not.toHaveBeenCalled();

    const createdAccountId = mockDatabaseService.createAccount.mock.calls[0][0];
    expect(mockDatabaseService.deleteAccount).toHaveBeenCalledWith(createdAccountId);
  });

  it('falls back to closeAccount when delete compensation fails', async () => {
    mockDatabaseService.createAccount.mockResolvedValue(true);
    mockUsersRepository.createUserWithCredentialAndTradingLink.mockRejectedValue(
      new Error('Auth DB transaction failed'),
    );
    mockDatabaseService.deleteAccount.mockRejectedValue(new Error('Delete failed'));
    mockDatabaseService.closeAccount.mockResolvedValue(true);

    await request(app.getHttpServer())
      .post('/api/auth/register')
      .send({
        username: 'bob',
        email: 'bob@example.com',
        fullName: 'Bob Smith',
        password: 'SecurePass123!',
      })
      .expect(400);

    expect(mockDatabaseService.deleteAccount).toHaveBeenCalledTimes(1);
    expect(mockDatabaseService.closeAccount).toHaveBeenCalledTimes(1);

    const createdAccountId = mockDatabaseService.createAccount.mock.calls[0][0];
    expect(mockDatabaseService.closeAccount).toHaveBeenCalledWith(createdAccountId);
  });
});