import { DatabaseService } from './database.service';

describe('DatabaseService', () => {
  let service: DatabaseService;
  let client: {
    query: jest.Mock;
    release: jest.Mock;
  };
  let tradingDbPool: {
    connect: jest.Mock;
    end: jest.Mock;
  };

  beforeEach(() => {
    client = {
      query: jest.fn(),
      release: jest.fn(),
    };

    tradingDbPool = {
      connect: jest.fn().mockResolvedValue(client),
      end: jest.fn().mockResolvedValue(undefined),
    };

    service = new DatabaseService();

    // Replace the real pool with a mock pool.
    (service as any).tradingDbPool = tradingDbPool;
  });

  it('creates an account and commits the transaction', async () => {
    client.query.mockResolvedValue(undefined);

    await expect(
      service.createAccount('ACC-100', 'Alice'),
    ).resolves.toBe(true);

    expect(client.query).toHaveBeenNthCalledWith(1, 'BEGIN');
    expect(client.query).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining('INSERT INTO accounts'),
      ['ACC-100', 'Alice', 0, 'ACTIVE'],
    );
    expect(client.query).toHaveBeenNthCalledWith(3, 'COMMIT');
    expect(client.release).toHaveBeenCalledTimes(1);
  });

  it('rolls back when account creation fails', async () => {
    const databaseError = new Error('insert failed');

    client.query
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(databaseError)
      .mockResolvedValueOnce(undefined);

    await expect(
      service.createAccount('ACC-101', 'Bob'),
    ).rejects.toThrow('insert failed');

    expect(client.query).toHaveBeenCalledWith('ROLLBACK');
    expect(client.release).toHaveBeenCalledTimes(1);
  });

  it('closes an existing account', async () => {
    client.query.mockResolvedValue({ rowCount: 1 });

    await expect(
      service.closeAccount('ACC-102'),
    ).resolves.toBe(true);

    expect(client.query).toHaveBeenCalledWith(
      expect.stringContaining('UPDATE accounts'),
      ['CLOSED', 'ACC-102'],
    );
    expect(client.release).toHaveBeenCalledTimes(1);
  });

  it('rejects closing an account that does not exist', async () => {
    client.query.mockResolvedValue({ rowCount: 0 });

    await expect(
      service.closeAccount('ACC-103'),
    ).rejects.toThrow('Account not found: ACC-103');

    expect(client.release).toHaveBeenCalledTimes(1);
  });

  it('deletes an existing account', async () => {
    client.query.mockResolvedValue({ rowCount: 1 });

    await expect(
      service.deleteAccount('ACC-104'),
    ).resolves.toBe(true);

    expect(client.query).toHaveBeenCalledWith(
      expect.stringContaining('DELETE FROM accounts'),
      ['ACC-104'],
    );
    expect(client.release).toHaveBeenCalledTimes(1);
  });

  it('rejects deleting an account that does not exist', async () => {
    client.query.mockResolvedValue({ rowCount: 0 });

    await expect(
      service.deleteAccount('ACC-105'),
    ).rejects.toThrow('Account not found for delete: ACC-105');

    expect(client.release).toHaveBeenCalledTimes(1);
  });

  it('returns the database pool', async () => {
    await expect(service.getConnection()).resolves.toBe(tradingDbPool);
  });

  it('closes the database pool', async () => {
    await service.closeConnection();

    expect(tradingDbPool.end).toHaveBeenCalledTimes(1);
  });
});