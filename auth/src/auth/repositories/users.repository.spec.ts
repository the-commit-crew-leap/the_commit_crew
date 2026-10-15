import { UsersRepository } from './users.repository';

describe('UsersRepository login protection', () => {
  let repository: UsersRepository;
  let userRepository: {
    findOne: jest.Mock;
    update: jest.Mock;
  };
  let credentialRepository: {
    increment: jest.Mock;
    findOne: jest.Mock;
    update: jest.Mock;
  };
  let userTradingAccountRepository: {
    create: jest.Mock;
    save: jest.Mock;
    findOne: jest.Mock;
    };
  let dataSource: Record<string, jest.Mock>;

    beforeEach(() => {
    dataSource = {};
    userRepository = {
        findOne: jest.fn(),
        update: jest.fn(),
    };

    credentialRepository = {
        increment: jest.fn().mockResolvedValue(undefined),
        findOne: jest.fn(),
        update: jest.fn().mockResolvedValue(undefined),
    };

    userTradingAccountRepository = {
        create: jest.fn(),
        save: jest.fn().mockResolvedValue(undefined),
        findOne: jest.fn(),
    };

    repository = new UsersRepository(
        userRepository as any,
        credentialRepository as any,
        userTradingAccountRepository as any,
        dataSource as any,
    );
    });

  it('does not lock an account before the fifth failed attempt', async () => {
    credentialRepository.findOne.mockResolvedValue({
      failedLoginAttempts: 4,
    });

    await repository.incrementFailedLoginAttempts(100);

    expect(credentialRepository.increment).toHaveBeenCalledWith(
      { userId: 100 },
      'failedLoginAttempts',
      1,
    );
    expect(credentialRepository.update).not.toHaveBeenCalled();
    expect(userRepository.update).not.toHaveBeenCalled();
  });

  it('locks an account after the fifth failed attempt for 15 minutes', async () => {
    const currentTime = new Date('2026-10-14T15:00:00.000Z');
    jest.useFakeTimers();
    jest.setSystemTime(currentTime);

    credentialRepository.findOne.mockResolvedValue({
      failedLoginAttempts: 5,
    });

    await repository.incrementFailedLoginAttempts(101);

    expect(credentialRepository.update).toHaveBeenCalledWith(
      { userId: 101 },
      {
        lockedUntil: new Date(currentTime.getTime() + 15 * 60 * 1000),
      },
    );

    expect(userRepository.update).toHaveBeenCalledWith(
      { userId: 101 },
      { status: 'SUSPENDED' },
    );

    jest.useRealTimers();
  });

  it('finds a user by username', async () => {
    const user = { userId: 1, username: 'alice' };
    userRepository.findOne.mockResolvedValue(user);

    await expect(repository.findByUsername('alice')).resolves.toBe(user);

    expect(userRepository.findOne).toHaveBeenCalledWith({
        where: { username: 'alice' },
    });
    });

    it('finds a user with credentials', async () => {
    userRepository.findOne.mockResolvedValue({ userId: 1 });

    await repository.findByUsernameWithCredential('alice');

    expect(userRepository.findOne).toHaveBeenCalledWith({
        where: { username: 'alice' },
        relations: { credential: true },
    });
    });

    it('finds a user with credentials and trading accounts', async () => {
    userRepository.findOne.mockResolvedValue({ userId: 1 });

    await repository.findByUsernameWithCredentialAndTradingAccounts('alice');

    expect(userRepository.findOne).toHaveBeenCalledWith({
        where: { username: 'alice' },
        relations: { credential: true, tradingAccounts: true },
    });
    });

    it('finds a user by ID', async () => {
    userRepository.findOne.mockResolvedValue({ userId: 42 });

    await repository.findById(42);

    expect(userRepository.findOne).toHaveBeenCalledWith({
        where: { userId: 42 },
        relations: { credential: true },
    });
    });

    it('finds a user by email', async () => {
    userRepository.findOne.mockResolvedValue({ userId: 1 });

    await repository.findByEmail('ALICE@example.com');

    expect(userRepository.findOne).toHaveBeenCalledTimes(1);
    expect(userRepository.findOne.mock.calls[0][0].where.email).toBeDefined();
    });

    it('links a user to a trading account', async () => {
    const accountLink = {
        userId: 42,
        accountId: 'ACC-42',
        role: 'CLIENT',
    };

    userTradingAccountRepository.create.mockReturnValue(accountLink);

    await repository.linkUserToTradingAccount(42, 'ACC-42', 'CLIENT');

    expect(userTradingAccountRepository.create).toHaveBeenCalledWith(accountLink);
    expect(userTradingAccountRepository.save).toHaveBeenCalledWith(accountLink);
    });

    it('finds a trading account by user ID', async () => {
    userTradingAccountRepository.findOne.mockResolvedValue({
        userId: 42,
        accountId: 'ACC-42',
    });

    await repository.findTradingAccountByUserId(42);

    expect(userTradingAccountRepository.findOne).toHaveBeenCalledWith({
        where: { userId: 42 },
    });
    });

    it('updates the last login time', async () => {
    await repository.updateLastLogin(42);

    expect(credentialRepository.update).toHaveBeenCalledWith(
        { userId: 42 },
        { lastLogin: expect.any(Date) },
    );
    });

  it('resets failed attempts and reactivates a suspended account', async () => {
    await repository.resetFailedLoginAttempts(102);

    expect(credentialRepository.update).toHaveBeenCalledWith(
      { userId: 102 },
      {
        failedLoginAttempts: 0,
        lockedUntil: null,
      },
    );

    expect(userRepository.update).toHaveBeenCalledWith(
      { userId: 102, status: 'SUSPENDED' },
      { status: 'ACTIVE' },
    );
  });
});